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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
 * Thread safety: {@code ZipFileSystem.readFullyAt} reads {@code FileChannel}s with positional reads and no lock; for
 * any other channel it locks the channel and seeks. The swap happens while holding the old channel's lock, so a reader
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

    private static final Object PASS_LOCK = new Object();

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
        if (Runtime.version().feature() != 21) {
            Patchwork.LOG.info("[Patchwork] nestedjars: written for Java 21, running on {}; not touching anything", Runtime.version());
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
                moved++;
                freed += buf.length;
            }
        }
        if (moved + skipped > 0 || "construct".equals(when)) {
            Patchwork.LOG.info("[Patchwork] nestedjars ({}): {} nested jars now read from disk ({} already cached), {} MB freed, {} skipped, {} ms",
                    when, moved, reused, freed / (1024 * 1024), skipped, (System.nanoTime() - start) / 1_000_000);
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
