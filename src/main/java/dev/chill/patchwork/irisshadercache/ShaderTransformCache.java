package dev.chill.patchwork.irisshadercache;

import dev.chill.patchwork.Patchwork;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import org.lwjgl.opengl.GL11C;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Iris re-runs its GLSL transformer (parse, patch, print) for every shader program after each game start, because its
 * TransformPatcher cache lives in memory only. The transform is a pure function of the input sources and the Parameters
 * object (Iris's own cache key), so the output is kept in {@code cache/patchwork/iris-shaders/<sha256>.bin}, keyed by a
 * hash of exactly that plus everything that could change the transformer itself (mod list with versions, GL driver).
 * Anything the key can't describe exactly (an unknown field type) is transformed normally and not stored.
 * With {@code -Dpatchwork.verify.irisshadercache=true} every hit is also transformed for real and compared.
 */
public final class ShaderTransformCache {

    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve("cache").resolve("patchwork").resolve("iris-shaders");
    /** Pruned back to PRUNE_TO (least recently used first) once per launch when the folder is larger than MAX_BYTES. */
    private static final long MAX_BYTES = 64L << 20, PRUNE_TO = 48L << 20;
    /** Parameters fields that are scratch state, not inputs: name only appears in error messages, type is set per stage */
    private static final Set<String> SKIP_FIELDS = Set.of("name", "type");
    private static final boolean VERIFY = Boolean.getBoolean("patchwork.verify.irisshadercache");
    private static String prefix;
    private static boolean pruned;
    private static int hits, misses, uncacheable, mismatches;

    private ShaderTransformCache() {}

    public static Map<PatchShaderType, String> transform(String name, Map<PatchShaderType, String> inputs, Parameters p,
                                                         Supplier<Map<PatchShaderType, String>> real) {
        String key;
        try {
            key = key(inputs, p);
        } catch (Throwable t) {
            uncacheable++;
            return real.get();
        }
        prune();
        Path f = DIR.resolve(key + ".bin");
        Map<PatchShaderType, String> cached = read(f);
        if (cached != null) {
            hits++;
            touch(f);
            if (VERIFY) {
                Map<PatchShaderType, String> out = real.get();
                if (!out.equals(cached)) {
                    mismatches++;
                    Patchwork.LOG.error("[Patchwork] irisshadercache MISMATCH for {}", name);
                }
                logCounts();
                return out;
            }
            return cached;
        }
        misses++;
        Map<PatchShaderType, String> out = real.get();
        write(f, out);
        if (VERIFY) logCounts();
        return out;
    }

    private static void logCounts() {
        Patchwork.LOG.info("[Patchwork] irisshadercache hits={} misses={} uncacheable={} mismatches={}", hits, misses, uncacheable, mismatches);
    }

    private static String key(Map<PatchShaderType, String> inputs, Parameters p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(prefix().getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        canon(p, sb, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        md.update(sb.toString().getBytes(StandardCharsets.UTF_8));
        for (PatchShaderType t : PatchShaderType.values()) {
            String s = inputs.get(t);
            md.update((byte) (s == null ? 0 : 1));
            if (s != null) {
                md.update(s.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static synchronized String prefix() {
        if (prefix == null) {
            StringBuilder sb = new StringBuilder("v1|");
            List<String> mods = new ArrayList<>();
            for (IModInfo m : ModList.get().getMods()) mods.add(m.getModId() + "@" + m.getVersion());
            Collections.sort(mods);
            sb.append(String.join(",", mods)).append('|');
            sb.append(GL11C.glGetString(GL11C.GL_VENDOR)).append('|').append(GL11C.glGetString(GL11C.GL_RENDERER))
                    .append('|').append(GL11C.glGetString(GL11C.GL_VERSION));
            prefix = sb.toString();
        }
        return prefix;
    }

    /** Deterministic dump of a key object; throws on anything it can't describe exactly, so that call is not cached. */
    private static void canon(Object o, StringBuilder sb, int depth, Set<Object> seen) throws IllegalAccessException {
        if (depth > 10) throw new IllegalStateException("too deep");
        if (o == null) { sb.append("null"); return; }
        Class<?> c = o.getClass();
        if (o instanceof String || o instanceof Number || o instanceof Boolean || o instanceof Character) {
            sb.append(c.getSimpleName()).append(':').append(o).append(';');
            return;
        }
        if (o instanceof Enum<?> e) { sb.append(e.getDeclaringClass().getName()).append('.').append(e.name()).append(';'); return; }
        if (!seen.add(o)) throw new IllegalStateException("cycle");
        if (o instanceof Map<?, ?> m) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                StringBuilder es = new StringBuilder();
                canon(e.getKey(), es, depth + 1, seen);
                es.append("=>");
                canon(e.getValue(), es, depth + 1, seen);
                entries.add(es.toString());
            }
            Collections.sort(entries);
            sb.append("map{").append(String.join(",", entries)).append('}');
        } else if (o instanceof Collection<?> col) {
            List<String> items = new ArrayList<>();
            for (Object i : col) {
                StringBuilder is = new StringBuilder();
                canon(i, is, depth + 1, seen);
                items.add(is.toString());
            }
            if (o instanceof Set<?>) Collections.sort(items);
            sb.append("col[").append(String.join(",", items)).append(']');
        } else if (c.isArray()) {
            sb.append("arr[");
            for (int i = 0; i < Array.getLength(o); i++) canon(Array.get(o, i), sb, depth + 1, seen);
            sb.append(']');
        } else if (c.getName().startsWith("net.irisshaders.")) {
            sb.append(c.getName()).append('{');
            List<Field> fields = new ArrayList<>();
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                    if (k == Parameters.class && SKIP_FIELDS.contains(f.getName())) continue;
                    fields.add(f);
                }
            }
            fields.sort(Comparator.comparing((Field f) -> f.getDeclaringClass().getName()).thenComparing(Field::getName));
            for (Field f : fields) {
                f.setAccessible(true);
                sb.append(f.getName()).append('=');
                canon(f.get(o), sb, depth + 1, seen);
            }
            sb.append('}');
        } else {
            throw new IllegalStateException("unknown key type " + c.getName());
        }
        seen.remove(o);
    }

    private static Map<PatchShaderType, String> read(Path f) {
        if (!Files.isRegularFile(f)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(f)))) {
            Map<PatchShaderType, String> m = new EnumMap<>(PatchShaderType.class);
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                PatchShaderType t = PatchShaderType.valueOf(in.readUTF());
                int len = in.readInt();
                if (len < 0) { m.put(t, null); continue; }
                byte[] b = new byte[len];
                in.readFully(b);
                m.put(t, new String(b, StandardCharsets.UTF_8));
            }
            return m;
        } catch (Exception e) {
            Patchwork.LOG.warn("[Patchwork] irisshadercache: can't read {}", f, e);
            return null;
        }
    }

    private static void write(Path f, Map<PatchShaderType, String> m) {
        if (m == null) return;
        try {
            Files.createDirectories(DIR);
            Path tmp = Files.createTempFile(DIR, "w", ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(m.size());
                for (Map.Entry<PatchShaderType, String> e : m.entrySet()) {
                    out.writeUTF(e.getKey().name());
                    if (e.getValue() == null) { out.writeInt(-1); continue; }
                    byte[] b = e.getValue().getBytes(StandardCharsets.UTF_8);
                    out.writeInt(b.length);
                    out.write(b);
                }
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Patchwork.LOG.warn("[Patchwork] irisshadercache: can't write {}", f, e);
        }
    }

    /** Marks a hit as recently used, so pruning drops entries of old packs, settings and mod lists first. */
    private static void touch(Path f) {
        try {
            Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
        }
    }

    private static void prune() {
        if (pruned) return;
        pruned = true;
        if (!Files.isDirectory(DIR)) return;
        try (Stream<Path> s = Files.list(DIR)) {
            List<Path> files = new ArrayList<>(s.filter(Files::isRegularFile).toList());
            Map<Path, Long> size = new HashMap<>(), time = new HashMap<>();
            long total = 0;
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
