package dev.chill.patchwork.subtleeffects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-client-tick memo for Subtle Effects' ambient checks. Subtle Effects runs its checks for every random block vanilla
 * animates (about 1,300 a tick), and each check asks the same questions again: the biome at that block up to five times, the
 * same config conditions dozens of times, and long config lists scanned element by element. The answers cannot change within
 * one client tick, so they are worked out once per tick and reused. Everything is dropped at the start of each tick, so a
 * config change or a biome change shows up on the next tick, exactly when it would have without the cache.
 * Render thread only; any other thread falls straight through to the original call.
 */
public final class SubtleCache {

    /** Lists shorter than this are scanned directly; a set lookup only wins for longer lists. */
    private static final int MIN_SET_SIZE = 6;

    private static int epoch;
    private static boolean enabled = true;
    private static int biomeEpoch = -1, conditionEpoch = -1, setEpoch = -1;
    private static Level biomeLevel;
    private static final Long2ObjectOpenHashMap<Holder<Biome>> BIOMES = new Long2ObjectOpenHashMap<>();
    private static final Map<Object, Object> CONDITIONS = new IdentityHashMap<>();
    private static final Map<List<?>, Set<Object>> SETS = new IdentityHashMap<>();

    private SubtleCache() {}

    /** Called at the start of every client tick. */
    public static void nextTick() {
        epoch++;
        // Whether the module runs at all is decided at launch (patchwork.toml). patchbox.abOff (shared name, kept) is set only by the test
        // bench, to flip the cache off mid-run for same-run A/B timing.
        enabled = !Boolean.getBoolean("patchbox.abOff");
    }

    /** -Dpatchwork.verify.subtleeffects=true: every cached answer is also computed fresh and mismatches are logged. */
    public static final boolean VERIFY = Boolean.getBoolean("patchwork.verify.subtleeffects");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long checks, mismatches;

    public static void check(String what, boolean same, Object detail) {
        checks++;
        if (!same && mismatches++ < 50) {
            LOG.warn("[Patchwork] subtleeffects verify MISMATCH in {}: {}", what, detail);
        }
        if ((checks & 0xFFFFF) == 0) {
            LOG.info("[Patchwork] subtleeffects verify: {} checks, {} mismatches", checks, mismatches);
        }
    }

    public static Holder<Biome> biome(Level level, BlockPos pos, Operation<Holder<Biome>> original) {
        if (!enabled || !level.isClientSide() || !RenderSystem.isOnRenderThread()) {
            return original.call(level, pos);
        }
        if (biomeEpoch != epoch || biomeLevel != level) {
            BIOMES.clear();
            biomeEpoch = epoch;
            biomeLevel = level;
        }
        long key = pos.asLong();
        Holder<Biome> biome = BIOMES.get(key);
        if (biome == null) {
            biome = original.call(level, pos);
            BIOMES.put(key, biome);
        } else if (VERIFY) {
            Holder<Biome> fresh = original.call(level, pos);
            check("biome", fresh.equals(biome), pos);
        }
        return biome;
    }

    public static Object condition(Object condition, Operation<Object> original) {
        if (!enabled || !RenderSystem.isOnRenderThread()) {
            return original.call(condition);
        }
        if (conditionEpoch != epoch) {
            CONDITIONS.clear();
            conditionEpoch = epoch;
        }
        Object value = CONDITIONS.get(condition);
        if (value != null && VERIFY) {
            Object fresh = original.call(condition);
            check("condition", java.util.Objects.equals(fresh, value), condition);
        }
        if (value == null) {
            value = original.call(condition);
            if (value != null) {
                CONDITIONS.put(condition, value);
            }
        }
        return value;
    }

    /** Plain config fields are cheap; only conditional ones (which re-check every condition on each call) are memoised. */
    public static Object field(Object field, Operation<Object> original) {
        return field instanceof me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<?> ? condition(field, original) : original.call(field);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static boolean contains(List<?> list, Object element, Operation<Boolean> original) {
        if (!enabled || list.size() < MIN_SET_SIZE || !RenderSystem.isOnRenderThread()) {
            return original.call(list, element);
        }
        if (setEpoch != epoch) {
            SETS.clear();
            setEpoch = epoch;
        }
        Set<Object> set = SETS.get(list);
        if (set == null) {
            set = new HashSet<>(list);
            SETS.put(list, set);
        }
        boolean result = set.contains(element);
        if (VERIFY) {
            check("contains", result == original.call(list, element), element);
        }
        return result;
    }
}
