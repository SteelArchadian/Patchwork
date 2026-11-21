package dev.chill.patchwork.betterendcaves;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Better End fills its cave biome picker (EndBiomes.CAVE_BIOMES) from the betterend:is_end_cave tag when the End level
 * is ready. If the tag is not bound yet at that moment (a load-order race), the picker stays empty and never refills
 * for that world: every End cave then gets the fallback minecraft:end_highlands, Better End logs "is not an
 * EndCaveBiome" and carves those caves bare. Here, cave lookups check the picker until it is known to be good; if it
 * is empty, the level-ready step runs again (the tag is bound by then), at most once a second and 20 times per world.
 * Until the picker is confirmed, lookups are serialized so no worldgen thread sees the map while it is rebuilt.
 */
public final class CavePickerGuard {
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    private static final Object LOCK = new Object();
    private static final int MAX_TRIES = 20;

    /** Volatile so the hot path is one read once the picker is known to be good. */
    public static volatile boolean settled;

    private static Class<?> owner;
    private static Object[] args;
    private static int tries;
    private static long lastTry;
    private static boolean failed;
    private static boolean rebuilding;
    private static boolean testCleared;
    private static String why = "?";

    private CavePickerGuard() {
    }

    /** HEAD of EndBiomes.onServerLevelReady. Our own re-invocation is ignored. */
    public static void levelReady(Class<?> endBiomes, Object level, Object key, Object stem, long seed) {
        synchronized (LOCK) {
            if (rebuilding) return;
            if (args != null && !String.valueOf(key).contains("the_end")) return;
            owner = endBiomes;
            args = new Object[]{level, key, stem, seed};
            tries = 0;
            lastTry = 0;
            settled = false;
        }
    }

    /** TAIL of onServerLevelReady: test hook that empties the picker once, as the race does. */
    public static void testClear(Class<?> endBiomes) {
        if (rebuilding || testCleared || !Boolean.getBoolean("patchwork.test.betterendcaves.empty")) return;
        testCleared = true;
        try {
            Object picker = endBiomes.getField("CAVE_BIOMES").get(null);
            LOG.info("[Patchwork] betterendcaves: test: picker had {} biomes before clear", size(picker));
            Field biomes = picker.getClass().getDeclaredField("biomes");
            Field registered = picker.getClass().getDeclaredField("registeredBiomes");
            biomes.setAccessible(true);
            registered.setAccessible(true);
            ((Collection<?>) biomes.get(picker)).clear();
            ((Map<?, ?>) registered.get(picker)).clear();
            picker.getClass().getMethod("rebuild").invoke(picker);
            LOG.info("[Patchwork] betterendcaves: test: cave biome picker emptied");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[Patchwork] betterendcaves: test clear failed ({})", e.toString());
        }
    }

    /** HEAD of EndBiomes.getCaveBiome while not settled. */
    public static void check() {
        synchronized (LOCK) {
            if (settled || failed || owner == null) return;
            try {
                Field caveField = owner.getField("CAVE_BIOMES");
                Object picker = caveField.get(null);
                if (picker != null && !isEmpty(picker)) {
                    if (tries > 0) {
                        LOG.info("[Patchwork] betterendcaves: cave biome picker rebuilt ({} biomes) after {} tries",
                                size(picker), tries);
                    }
                    settled = true;
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastTry < 1000) return;
                if (tries >= MAX_TRIES) {
                    LOG.warn("[Patchwork] betterendcaves: cave biome tag still empty after {} tries; leaving Better End as is", tries);
                    settled = true;
                    return;
                }
                tries++;
                lastTry = now;
                if (tries == 1) LOG.info("[Patchwork] betterendcaves: cave biome picker is empty; rebuilding");
                refill();
                Object rebuilt = caveField.get(null);
                if (rebuilt != null && !isEmpty(rebuilt)) {
                    LOG.info("[Patchwork] betterendcaves: cave biome picker rebuilt ({} biomes) after {} tries",
                            size(rebuilt), tries);
                    settled = true;
                } else if (tries == 1) {
                    LOG.info("[Patchwork] betterendcaves: refill found nothing: {}; retrying", why);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                settled = true;
                LOG.warn("[Patchwork] betterendcaves: Better End layout not as expected, module off ({})", e.toString());
            }
        }
    }

    /** TAIL of onServerLevelReady: repair right away, before any worldgen thread asks. */
    public static void afterReady() {
        synchronized (LOCK) {
            if (rebuilding || failed || owner == null) return;
            try {
                Object picker = owner.getField("CAVE_BIOMES").get(null);
                if (picker != null && !isEmpty(picker)) return;
                refill();
                Object rebuilt = owner.getField("CAVE_BIOMES").get(null);
                if (rebuilt != null && !isEmpty(rebuilt)) {
                    LOG.info("[Patchwork] betterendcaves: cave biome picker was empty at level start; refilled ({} biomes) from {}",
                            size(rebuilt), args[1]);
                    settled = true;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                settled = true;
                LOG.warn("[Patchwork] betterendcaves: Better End layout not as expected, module off ({})", e.toString());
            }
        }
    }

    /**
     * Better End reads the tag through Wover's WorldState registry access, which on a freshly created world is the
     * world-creation copy whose tags are never bound. Fill a new picker the same way Better End does, but from the
     * server level's own registries, and rebuild the cave map from it.
     */
    private static void refill() throws ReflectiveOperationException {
        ServerLevel level = (ServerLevel) args[0];
        long seed = (Long) args[3];
        ClassLoader cl = owner.getClassLoader();
        RegistryAccess access = level.registryAccess();
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        @SuppressWarnings("unchecked")
        TagKey<Biome> tag = (TagKey<Biome>) Class.forName("org.betterx.betterend.registry.EndTags", true, cl)
                .getField("IS_END_CAVE").get(null);
        @SuppressWarnings("unchecked")
        ResourceKey<Registry<Object>> dataKey = (ResourceKey<Registry<Object>>) Class.forName(
                "org.betterx.wover.biome.api.data.BiomeDataRegistry", true, cl).getField("BIOME_DATA_REGISTRY").get(null);
        Registry<Object> data = access.registry(dataKey).orElse(null);
        if (data == null) { why = "no biome data registry " + dataKey.location(); return; }
        Field caveField = owner.getField("CAVE_BIOMES");
        Class<?> pickerType = caveField.getType();
        Object picker = pickerType.getConstructor(Registry.class, ResourceKey.class).newInstance(biomes, Biomes.END_HIGHLANDS);
        Method add = null;
        for (Method m : pickerType.getMethods()) {
            if (m.getName().equals("addBiome") && m.getParameterCount() == 1) add = m;
        }
        if (add == null) throw new NoSuchMethodException("addBiome");
        Optional<HolderSet.Named<Biome>> set = biomes.getTag(tag);
        if (set.isEmpty()) {
            long tagged = biomes.getTags().count();
            why = "tag " + tag.location() + " not in server biome registry (" + tagged + " biome tags, "
                    + biomes.size() + " biomes, cave biome present: "
                    + biomes.containsKey(ResourceLocation.fromNamespaceAndPath("betterend", "lush_aurora_cave")) + ")";
            return;
        }
        int n = 0, missing = 0;
        for (Holder<Biome> h : set.get()) {
            Optional<ResourceKey<Biome>> key = h.unwrapKey();
            if (key.isEmpty()) continue;
            Object d = data.get(key.get().location());
            n++;
            if (d != null) add.invoke(picker, d); else missing++;
        }
        why = "tag has " + n + " biomes, " + missing + " without biome data (data registry size " + data.size() + ")";
        pickerType.getMethod("rebuild").invoke(picker);
        if (isEmpty(picker)) return;
        Field mapField = owner.getDeclaredField("caveBiomeMap");
        mapField.setAccessible(true);
        int size = (Integer) Class.forName("org.betterx.betterend.world.generator.GeneratorOptions", true, cl)
                .getMethod("getBiomeSizeCaves").invoke(null);
        Object map = mapField.getType().getConstructor(long.class, int.class, pickerType).newInstance(seed, size, picker);
        caveField.set(null, picker);
        mapField.set(null, map);
    }

    private static boolean isEmpty(Object picker) throws ReflectiveOperationException {
        return (Boolean) picker.getClass().getMethod("isEmpty").invoke(picker);
    }

    private static int size(Object picker) {
        try {
            Field biomes = picker.getClass().getDeclaredField("biomes");
            biomes.setAccessible(true);
            return ((Collection<?>) biomes.get(picker)).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
