package dev.chill.patchwork.woverregistrypath;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge puts a non-minecraft registry's namespace in front of its data folder (wover:wover/worldgen/biome_data is
 * read from data/<ns>/wover/wover/worldgen/biome_data). WorldWeaver's datapack registries ship their files without
 * that prefix (data/betterend/wover/worldgen/biome_data), as on Fabric, so on NeoForge (through Connector) every one
 * of them loads empty: Better End's biome data registry has 0 entries, its cave biome picker stays empty and End caves
 * get end_highlands ("is not an EndCaveBiome"). For registries WorldWeaver registered, the folder is the plain path.
 */
public final class WoverRegistryPath {
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    private static final Set<ResourceLocation> LOGGED = ConcurrentHashMap.newKeySet();
    private static volatile Method isRegistered;
    private static volatile boolean failed;

    private WoverRegistryPath() {
    }

    /** The fixed folder, or null to keep NeoForge's. */
    public static String fix(ResourceKey<?> key, String dir) {
        if (failed || dir == null) return null;
        ResourceLocation id = key.location();
        String ns = id.getNamespace();
        if (ResourceLocation.DEFAULT_NAMESPACE.equals(ns)) return null;
        if (!dir.equals(ns + "/" + id.getPath())) {
            return null;
        }
        try {
            Method m = isRegistered;
            if (m == null) {
                Class<?> c = Class.forName("org.betterx.wover.core.impl.registry.DatapackRegistryBuilderImpl");
                m = c.getMethod("isRegistered", ResourceLocation.class);
                isRegistered = m;
            }
            if (!(Boolean) m.invoke(null, id)) {
                return null;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            LOG.warn("[Patchwork] woverregistrypath: WorldWeaver layout not as expected, module off ({})", e.toString());
            return null;
        }
        if (LOGGED.add(id)) {
            LOG.info("[Patchwork] woverregistrypath: {} reads data/<ns>/{} (was {})", id, id.getPath(), dir);
        }
        return id.getPath();
    }

    private static volatile boolean added;

    /**
     * NeoForge copies the vanilla registry list once; WorldWeaver's own registries may not be in that copy, so they
     * never load from data packs. Add any that are missing (once, after WorldWeaver has registered them).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addMissing(java.util.List<net.minecraft.resources.RegistryDataLoader.RegistryData<?>> list) {
        if (added || failed) return;
        synchronized (WoverRegistryPath.class) {
            if (added || failed) return;
            try {
                Class<?> c = Class.forName("org.betterx.wover.core.impl.registry.DatapackRegistryBuilderImpl");
                Method each = c.getMethod("forEach", java.util.function.BiConsumer.class);
                java.util.List<Object[]> found = new java.util.ArrayList<>();
                each.invoke(null, (java.util.function.BiConsumer<Object, Object>) (k, codec) -> found.add(new Object[]{k, codec}));
                if (found.isEmpty()) return;
                Set<ResourceKey<?>> have = new java.util.HashSet<>();
                for (var d : list) have.add(d.key());
                int n = 0;
                for (Object[] f : found) {
                    ResourceKey k = (ResourceKey) f[0];
                    if (have.contains(k)) continue;
                    list.add(new net.minecraft.resources.RegistryDataLoader.RegistryData(k, (com.mojang.serialization.Codec) f[1], false));
                    n++;
                    LOG.info("[Patchwork] woverregistrypath: added missing datapack registry {}", k.location());
                }
                LOG.info("[Patchwork] woverregistrypath: {} WorldWeaver registries, {} were missing from NeoForge's list", found.size(), n);
                added = true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                failed = true;
                LOG.warn("[Patchwork] woverregistrypath: WorldWeaver layout not as expected, module off ({})", e.toString());
            }
        }
    }
}
