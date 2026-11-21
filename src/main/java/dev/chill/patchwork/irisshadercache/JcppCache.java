package dev.chill.patchwork.irisshadercache;

import dev.chill.patchwork.Patchwork;
import net.irisshaders.iris.helpers.StringPair;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Before transforming, Iris runs every program of the shader pack through JCPP (the C preprocessor) on each pack load,
 * about 0.35 s of every shader reload in Test Dummy. JcppProcessor.glslPreprocessSource is a pure function of the
 * source and the environment defines (no file system; JCPP's only built-in macros are __LINE__, __FILE__ and
 * __COUNTER__), so its output is kept in {@code cache/patchwork/iris-jcpp/<sha256>.glsl}, keyed by both plus the Iris
 * version. Runs in which JCPP printed a warning or error are not stored, so those messages still show next time.
 * Iris calls this from its worker threads too, so the per-call state is thread-local.
 * With {@code -Dpatchwork.verify.irisshadercache=true} every hit is also preprocessed for real and compared.
 */
public final class JcppCache {

    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve("cache").resolve("patchwork").resolve("iris-jcpp");
    /** Pruned back to PRUNE_TO (least recently used first) once per launch when the folder is larger than MAX_BYTES. */
    private static final long MAX_BYTES = 64L << 20, PRUNE_TO = 48L << 20;
    private static final boolean VERIFY = Boolean.getBoolean("patchwork.verify.irisshadercache");
    private static String prefix;
    private static boolean pruned;
    private static final AtomicInteger hits = new AtomicInteger(), misses = new AtomicInteger(), unstored = new AtomicInteger(), mismatches = new AtomicInteger();
    /** The current call's key, and on a verified hit the cached text to compare with */
    private static final ThreadLocal<String> KEY = new ThreadLocal<>(), EXPECTED = new ThreadLocal<>();
    /** A full or read-only disk would otherwise log once for every program of every pack load */
    private static final AtomicBoolean warned = new AtomicBoolean();

    private JcppCache() {}

    /** At the head of the call: the cached output, or null to preprocess for real */
    public static String lookup(String source, Iterable<StringPair> defines) {
        KEY.remove();
        EXPECTED.remove();
        String key;
        try {
            key = key(source, defines);
        } catch (Throwable t) {
            return null;
        }
        prune();
        Path f = DIR.resolve(key + ".glsl");
        String cached = read(f);
        if (cached == null) {
            misses.incrementAndGet();
            KEY.set(key);
            return null;
        }
        hits.incrementAndGet();
        touch(f);
        if (VERIFY) {
            KEY.set(key);
            EXPECTED.set(cached);
            return null;
        }
        return cached;
    }

    /** At the return of a real run: compare (verify) or store, unless JCPP complained */
    public static void store(String out, Object listener) {
        String key = KEY.get(), expected = EXPECTED.get();
        KEY.remove();
        EXPECTED.remove();
        if (key == null) return;
        if (expected != null) {
            if (!expected.equals(out)) {
                mismatches.incrementAndGet();
                Patchwork.LOG.error("[Patchwork] irisshadercache jcpp MISMATCH for {}", key);
            }
            logCounts();
            return;
        }
        if (complained(listener)) unstored.incrementAndGet();
        else write(DIR.resolve(key + ".glsl"), out);
        if (VERIFY) logCounts();
    }

    private static void logCounts() {
        Patchwork.LOG.info("[Patchwork] irisshadercache jcpp hits={} misses={} unstored={} mismatches={}", hits, misses, unstored, mismatches);
    }

    /** JCPP's DefaultPreprocessorListener counts what it printed; Iris's #version/#extension markers are not counted */
    private static boolean complained(Object listener) {
        try {
            int w = (int) listener.getClass().getMethod("getWarnings").invoke(listener);
            int e = (int) listener.getClass().getMethod("getErrors").invoke(listener);
            return w + e > 0;
        } catch (Throwable t) {
            return true;
        }
    }

    private static String key(String source, Iterable<StringPair> defines) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(prefix().getBytes(StandardCharsets.UTF_8));
        // Iris lists some defines (the BIOME_* ids) from a HashMap whose order changes between launches. The order
        // doesn't change JCPP's output, only which value a repeated name ends with (the last), so the key uses that, sorted.
        TreeMap<String, String> sorted = new TreeMap<>();
        for (StringPair d : defines) sorted.put(String.valueOf(d.key()), d.value());
        for (Map.Entry<String, String> d : sorted.entrySet()) {
            md.update(d.getKey().getBytes(StandardCharsets.UTF_8));
            // A null value (bare "#define NAME") must not share a key with the text "null"
            if (d.getValue() == null) {
                md.update((byte) 3);
            } else {
                md.update((byte) 1);
                md.update(d.getValue().getBytes(StandardCharsets.UTF_8));
            }
            md.update((byte) 0);
        }
        md.update((byte) 2);
        md.update(source.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest());
    }

    private static synchronized String prefix() {
        if (prefix == null) {
            prefix = "jcpp1|" + ModList.get().getModContainerById("iris").map(c -> c.getModInfo().getVersion().toString()).orElse("?") + "|";
        }
        return prefix;
    }

    private static String read(Path f) {
        if (!Files.isRegularFile(f)) return null;
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (!warned.getAndSet(true)) Patchwork.LOG.warn("[Patchwork] irisshadercache: can't read {}", f, e);
            return null;
        }
    }

    private static void write(Path f, String s) {
        if (s == null) return;
        Path tmp = null;
        try {
            Files.createDirectories(DIR);
            tmp = Files.createTempFile(DIR, "w", ".tmp");
            Files.writeString(tmp, s, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
            if (!warned.getAndSet(true)) Patchwork.LOG.warn("[Patchwork] irisshadercache: can't write {} (further cache errors are not logged)", f, e);
        }
    }

    /** Marks a hit as recently used, so pruning drops entries of old packs and settings first. */
    private static void touch(Path f) {
        try {
            Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
        }
    }

    private static synchronized void prune() {
        if (pruned) return;
        pruned = true;
        if (!Files.isDirectory(DIR)) return;
        try (Stream<Path> s = Files.list(DIR)) {
            List<Path> files = new ArrayList<>(s.filter(Files::isRegularFile).toList());
            Map<Path, Long> size = new HashMap<>(), time = new HashMap<>();
            long total = 0;
            // Temp files left by a crash mid-write: an hour old is long past any write still running
            long stale = System.currentTimeMillis() - 3_600_000L;
            for (Iterator<Path> it = files.iterator(); it.hasNext(); ) {
                Path p = it.next();
                if (p.getFileName().toString().endsWith(".tmp") && Files.getLastModifiedTime(p).toMillis() < stale) {
                    Files.deleteIfExists(p);
                    it.remove();
                }
            }
            for (Path p : files) {
                long len = Files.size(p);
                size.put(p, len);
                time.put(p, Files.getLastModifiedTime(p).toMillis());
                total += len;
            }
            if (total <= MAX_BYTES) return;
            files.sort(Comparator.comparingLong(time::get));
            for (Path p : files) {
                if (total <= PRUNE_TO) break;
                Files.deleteIfExists(p);
                total -= size.get(p);
            }
        } catch (IOException ignored) {
        }
    }
}
