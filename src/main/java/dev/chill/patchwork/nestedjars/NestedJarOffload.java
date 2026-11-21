package dev.chill.patchwork.nestedjars;

import dev.chill.patchwork.Patchwork;
import net.neoforged.fml.loading.FMLPaths;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.spi.FileSystemProvider;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Moves jar-in-jar contents out of the heap.
 * <p>
 * NeoForge opens every jar bundled inside another mod's jar through its "jij" file system, which opens a JDK zip file
 * system on a path <i>inside</i> the outer zip. For such paths the JDK reads the whole inner jar into a
 * {@code ByteArrayChannel} and keeps it for the rest of the session (about 117 MB for 386 nested jars in Foundation,
 * including candidates the jar-in-jar selector discarded). This writes each one once to
 * {@code cache/patchwork/nested-jars/<sha256>.jar} and points the zip file system at a read-only {@link FileChannel}
 * of that file instead, so the byte arrays can be collected. Same bytes, read from disk (page cache) on demand.
 * <p>
 * Thread safety: {@code ZipFileSystem.readFullyAt} (Java 21; {@code readNBytesAt} via {@code readAt} on 25) reads
 * {@code FileChannel}s with positional reads and no lock; for any other channel it locks the channel and seeks. The swap happens while holding the old channel's lock, so a reader
 * that already took the lock finishes first, and every later reader sees the {@code FileChannel} and its lock-free
 * positional read. The old channel is not closed (a reader may still hold it); it is just dropped.
 * <p>
 * Everything is checked structurally first; if the JDK or JarJarFileSystems internals differ, nothing is touched.
 */
public final class NestedJarOffload {
    private static final String ZIPFS = "jdk.nio.zipfs.ZipFileSystem";
    private static final String BYTE_CHANNEL = "jdk.nio.zipfs.ByteArrayChannel";
    private static final String PATH_FS_PROVIDER = "net.neoforged.jarjar.nio.pathfs.PathFileSystemProvider";
    private static final String PATH_FS = "net.neoforged.jarjar.nio.pathfs.PathFileSystem";
    private static final String LAZY = "net.neoforged.jarjar.nio.util.Lazy";

    private static final String FILE_CHANNEL = "sun.nio.ch.FileChannelImpl";
    private static final Object PASS_LOCK = new Object();
    private static final Pattern CACHE_NAME = Pattern.compile("[0-9a-f]{64}\\.jar");
    private static final long STALE_TMP_MS = 60L * 60 * 1000;
    /** Cache files in use this session, across passes; guarded by PASS_LOCK. */
    private static final Set<String> USED = new HashSet<>();

    private NestedJarOffload() {}

    /** Runs one pass on a daemon thread, so it adds nothing to load time. Safe to call more than once. */
    public static void startPass(String when) {
        Thread thread = new Thread(() -> {
            try {
                synchronized (PASS_LOCK) {
                    pass(when);
                }
            } catch (Throwable t) {
                Patchwork.LOG.warn("[Patchwork] nestedjars: pass ({}) failed, nested jars stay in memory", when, t);
            }
        }, "Patchwork nested jars");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void pass(String when) throws Exception {
        // Checked against the zipfs source of 21 and 25.0.4 (same fields, same locking); other versions are left alone.
        int java = Runtime.version().feature();
        if (java != 21 && java != 25) {
            Patchwork.LOG.info("[Patchwork] nestedjars: written for Java 21 and 25, running on {}; not touching anything", Runtime.version());
            return;
        }
        Unsafe unsafe = unsafe();
        FileSystemProvider jij = findJijProvider();
        if (jij == null) {
            Patchwork.LOG.info("[Patchwork] nestedjars: no jij file system provider found; nothing to do");
            return;
        }

        Class<?> providerClass = superclassNamed(jij.getClass(), PATH_FS_PROVIDER);
        Class<?> pathFsClass = Class.forName(PATH_FS, false, providerClass.getClassLoader());
        Class<?> lazyClass = Class.forName(LAZY, false, providerClass.getClassLoader());
        Class<?> zipFsClass = Class.forName(ZIPFS, false, ClassLoader.getSystemClassLoader());
        Class<?> byteChannelClass = Class.forName(BYTE_CHANNEL, false, ClassLoader.getSystemClassLoader());

        long fileSystemsOff = offset(unsafe, providerClass, "fileSystems", Map.class);
        long innerOff = offset(unsafe, pathFsClass, "innerSystem", lazyClass);
        long lazyValueOff = offset(unsafe, lazyClass, "value", Object.class);
        long lazyInitOff = offset(unsafe, lazyClass, "initialized", Boolean.class);
        long chOff = offset(unsafe, zipFsClass, "ch", SeekableByteChannel.class);
        long bufOff = offset(unsafe, byteChannelClass, "buf", byte[].class);
        long lastOff = offset(unsafe, byteChannelClass, "last", int.class);
        // Pruning needs to know which cache files earlier passes put in use; without the path field it is skipped.
        Class<?> fileChannelClass = null;
        long pathOff = -1;
        try {
            fileChannelClass = Class.forName(FILE_CHANNEL, false, ClassLoader.getSystemClassLoader());
            pathOff = offset(unsafe, fileChannelClass, "path", String.class);
        } catch (ReflectiveOperationException e) {
            Patchwork.LOG.info("[Patchwork] nestedjars: can't see FileChannel paths ({}); old cache files are kept", e.toString());
        }

        // The provider guards its map with itself only while adding; copy it under that lock.
        Map<?, ?> fileSystems = (Map<?, ?>) unsafe.getObjectVolatile(jij, fileSystemsOff);
        List<Object> pathSystems;
        synchronized (fileSystems) {
            pathSystems = new ArrayList<>(fileSystems.values());
        }

        Path cacheDir = FMLPaths.GAMEDIR.get().resolve("cache").resolve("patchwork").resolve("nested-jars");
        // Reuse the cache from when Patchwork was called PatchBox instead of writing every jar again.
        Path oldCacheDir = FMLPaths.GAMEDIR.get().resolve("cache").resolve("patchbox");
        if (Files.isDirectory(oldCacheDir) && !Files.exists(cacheDir.getParent())) {
            Files.move(oldCacheDir, cacheDir.getParent());
        }
        Files.createDirectories(cacheDir);
        boolean verify = Boolean.getBoolean("patchwork.verify.nestedjars");

        int moved = 0, reused = 0, skipped = 0;
        long freed = 0;
        long start = System.nanoTime();
        for (Object pathFs : pathSystems) {
            if (pathFs == null || !pathFsClass.isInstance(pathFs)) {
                continue;
            }
            Object lazy = unsafe.getObjectVolatile(pathFs, innerOff);
            if (lazy == null || !Boolean.TRUE.equals(unsafe.getObjectVolatile(lazy, lazyInitOff))) {
                continue; // never opened, holds nothing
            }
            Object inner = unsafe.getObjectVolatile(lazy, lazyValueOff);
            if (!zipFsClass.isInstance(inner) || !((FileSystem) inner).isOpen()) {
                continue;
            }
            Object ch = unsafe.getObjectVolatile(inner, chOff);
            if (!byteChannelClass.isInstance(ch)) {
                // Already a file: if it is one of ours, keep it.
                if (pathOff >= 0 && fileChannelClass.isInstance(ch)
                        && unsafe.getObjectVolatile(ch, pathOff) instanceof String p
                        && cacheDir.equals(Path.of(p).getParent())) {
                    USED.add(Path.of(p).getFileName().toString());
                }
                continue; // already a file, or something we don't know
            }
            synchronized (ch) {
                // Optimal's copy of this pass may have swapped the channel between our read and the lock.
                if (unsafe.getObjectVolatile(inner, chOff) != ch) {
                    continue;
                }
                byte[] buf = (byte[]) unsafe.getObjectVolatile(ch, bufOff);
                int size = unsafe.getInt(ch, lastOff);
                if (buf == null || size <= 0 || size > buf.length) {
                    skipped++;
                    continue;
                }
                Path file = cacheDir.resolve(sha256(buf, size) + ".jar");
                if (Files.isRegularFile(file) && Files.size(file) == size) {
                    reused++;
                } else {
                    Path tmp = Files.createTempFile(cacheDir, "part", ".tmp");
                    try (FileChannel out = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                        ByteBuffer src = ByteBuffer.wrap(buf, 0, size);
                        while (src.hasRemaining()) {
                            out.write(src);
                        }
                    }
                    try {
                        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        // Another instance of the game wrote the same file first.
                        Files.deleteIfExists(tmp);
                        if (!Files.isRegularFile(file) || Files.size(file) != size) {
                            throw e;
                        }
                    }
                }
                FileChannel fc = FileChannel.open(file, StandardOpenOption.READ);
                if (fc.size() != size || (verify && !sameBytes(fc, buf, size))) {
                    fc.close();
                    Patchwork.LOG.warn("[Patchwork] nestedjars: cached copy {} does not match, left in memory", file.getFileName());
                    skipped++;
                    continue;
                }
                unsafe.putObjectVolatile(inner, chOff, fc);
                USED.add(file.getFileName().toString());
                moved++;
                freed += buf.length;
            }
        }
        if (moved + skipped > 0 || "construct".equals(when)) {
            Patchwork.LOG.info("[Patchwork] nestedjars ({}): {} nested jars now read from disk ({} already cached), {} MB freed, {} skipped, {} ms",
                    when, moved, reused, freed / (1024 * 1024), skipped, (System.nanoTime() - start) / 1_000_000);
        }
        if ("load complete".equals(when) && pathOff >= 0 && !USED.isEmpty()) {
            prune(cacheDir, USED, "no installed mod uses any more");
        }
    }

    /** Optimal does this fix instead and keeps its own cache, so ours is a leftover copy; clears it in the background. */
    public static void clearCache() {
        Thread thread = new Thread(() -> {
            Path cacheDir = FMLPaths.GAMEDIR.get().resolve("cache").resolve("patchwork").resolve("nested-jars");
            if (Files.isDirectory(cacheDir)) {
                synchronized (PASS_LOCK) {
                    prune(cacheDir, Set.of(), "Optimal keeps its own copy");
                }
            }
        }, "Patchwork nested jars");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /**
     * Deletes cached jars not in {@code keep} (left over from mod updates) and temp files from
     * interrupted writes. Only names this class writes are touched. Runs after the last pass, so every nested jar
     * this session reads from disk is in USED.
     */
    private static void prune(Path cacheDir, Set<String> keep, String why) {
        int deleted = 0;
        long bytes = 0;
        long now = System.currentTimeMillis();
        try (Stream<Path> files = Files.list(cacheDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                boolean oldJar = CACHE_NAME.matcher(name).matches() && !keep.contains(name);
                boolean staleTmp = name.startsWith("part") && name.endsWith(".tmp")
                        && now - Files.getLastModifiedTime(file).toMillis() > STALE_TMP_MS;
                if ((oldJar || staleTmp) && Files.isRegularFile(file)) {
                    long size = Files.size(file);
                    if (Files.deleteIfExists(file)) {
                        deleted++;
                        bytes += size;
                    }
                }
            }
        } catch (IOException e) {
            Patchwork.LOG.warn("[Patchwork] nestedjars: couldn't clear old cache files", e);
        }
        if (deleted > 0) {
            Patchwork.LOG.info("[Patchwork] nestedjars: removed {} old cache files ({} MB): {}",
                    deleted, bytes / (1024 * 1024), why);
        }
    }

    private static FileSystemProvider findJijProvider() {
        for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
            if ("jij".equals(provider.getScheme()) && superclassNamed(provider.getClass(), PATH_FS_PROVIDER) != null) {
                return provider;
            }
        }
        return null;
    }

    private static Class<?> superclassNamed(Class<?> c, String name) {
        for (; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** Field offset after checking the field exists with the expected type; throws (so nothing is touched) otherwise. */
    private static long offset(Unsafe unsafe, Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        if (f.getType() != type) {
            throw new NoSuchFieldException(owner.getName() + "." + name + " is " + f.getType().getName() + ", expected " + type.getName());
        }
        return unsafe.objectFieldOffset(f);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    private static String sha256(byte[] buf, int size) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(buf, 0, size);
        return HexFormat.of().formatHex(md.digest());
    }

    private static boolean sameBytes(FileChannel fc, byte[] buf, int size) throws IOException {
        ByteBuffer read = ByteBuffer.allocate(size);
        long pos = 0;
        while (read.hasRemaining()) {
            int n = fc.read(read, pos);
            if (n < 0) {
                return false;
            }
            pos += n;
        }
        return ByteBuffer.wrap(buf, 0, size).equals(read.flip());
    }
}
