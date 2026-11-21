package dev.chill.patchwork.villagerapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Villager API 1.26.9.6 looks for its villagerapi_config.json in every mod and every zip in resourcepacks/ and
 * villagerpacks/ on each pack repository reload (opening the Resource Packs screen, F3+T, every data pack reload),
 * opening each zip as a file system and reading each mod's pack.mcmeta: about 460 ms in Foundation. Packs without the
 * file are closed unused, so they are remembered and skipped next time: zips by size and modified time (a zip that
 * changes on disk is looked at again), mods for the whole run (mods cannot change while the game runs).
 * <p>
 * Names no Villager API types, so it is safe to load without the mod.
 */
public final class NoConfigPacks {
    private record Stamp(long size, long modified) {}

    private static final Map<Path, Stamp> WITHOUT_CONFIG = new ConcurrentHashMap<>();
    private static final Set<String> MODS_WITHOUT_CONFIG = ConcurrentHashMap.newKeySet();
    private static volatile Map<String, Path> modRoots;

    private NoConfigPacks() {}

    /** Villager API's mod id to root folder map, found once, without the mods already seen without a config. */
    public static Map<String, Path> modRoots(Supplier<Map<String, Path>> find) {
        Map<String, Path> all = modRoots;
        if (all == null) modRoots = all = java.util.Collections.unmodifiableMap(new HashMap<>(find.get()));
        if (MODS_WITHOUT_CONFIG.isEmpty()) return all;
        Map<String, Path> left = new HashMap<>(all);
        left.keySet().removeAll(MODS_WITHOUT_CONFIG);
        return left;
    }

    /** After Villager API looked at a mod: remember mods that have no config. */
    public static void modOpened(String modId, boolean hasConfig) {
        if (hasConfig) MODS_WITHOUT_CONFIG.remove(modId);
        else MODS_WITHOUT_CONFIG.add(modId);
    }

    /** True when the path is a zip or jar already seen without a villager pack config and unchanged since. */
    public static boolean knownWithout(Path path) {
        if (WITHOUT_CONFIG.isEmpty()) return false;
        Stamp seen = WITHOUT_CONFIG.get(path.toAbsolutePath());
        return seen != null && seen.equals(stamp(path));
    }

    /** After Villager API opened a pack from a folder: remember zips and jars that have no config. */
    public static void opened(Path path, boolean hasConfig) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (!name.endsWith(".zip") && !name.endsWith(".jar")) return;
        Path key = path.toAbsolutePath();
        Stamp now = hasConfig ? null : stamp(path);
        if (now == null) WITHOUT_CONFIG.remove(key);
        else WITHOUT_CONFIG.put(key, now);
    }

    private static Stamp stamp(Path path) {
        try {
            BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class);
            return a.isRegularFile() ? new Stamp(a.size(), a.lastModifiedTime().toMillis()) : null;
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }
}
