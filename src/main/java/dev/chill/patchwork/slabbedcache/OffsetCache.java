package dev.chill.patchwork.slabbedcache;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Slabbed works out how far each block sits lowered (getYOffset) again for every collision cell, and each answer walks
 * up to 16 nearby blocks. Spiders that climb (Stormie's Spiders) and every entity's ground check ask about the same
 * cells many times a tick. The answer depends only on the blocks, so it is kept per thread for at most one game tick
 * and dropped on any block change. Only answers to plain top-level questions are kept: while Slabbed is in the middle
 * of its own nested lookup (its thread-local guards set) the call runs as normal.
 */
public final class OffsetCache {
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    private static final int MAX_ENTRIES = 1 << 15;
    private static final AtomicLong CHANGES = new AtomicLong();
    private static final ThreadLocal<Table> TABLES = ThreadLocal.withInitial(Table::new);
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();
    private static final Field CONTEXT_SIZE = contextSize();
    private static volatile boolean failed = CONTEXT_SIZE == null;

    private OffsetCache() {
    }

    private record Scope(BlockGetter getter, Level level) {
    }

    private record Entry(BlockState state, double value) {
    }

    @SuppressWarnings("unchecked")
    private static final class Table {
        Level level;
        long time = Long.MIN_VALUE;
        long stamp = -1;
        int size;
        // [view: 0 the level itself, 1 a collision view over it][consultStoredHeight]
        final Long2ObjectOpenHashMap<Entry>[] maps = new Long2ObjectOpenHashMap[]{
                new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>(),
                new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>()};

        void reset(Level newLevel, long newTime, long newStamp) {
            if (size > 0) {
                for (Long2ObjectOpenHashMap<Entry> map : maps) map.clear();
                size = 0;
            }
            level = newLevel;
            time = newTime;
            stamp = newStamp;
        }
    }

    private static Field contextSize() {
        try {
            Field f = Class.forName("com.slabbed.util.SlabSupport$SupportResolutionContext").getDeclaredField("size");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[Patchwork] slabbedcache: cannot read Slabbed's lookup state, offsets are not cached ({})", e.toString());
            return null;
        }
    }

    /** Any block change in a loaded chunk drops every kept answer. */
    public static void blockChanged() {
        CHANGES.incrementAndGet();
    }

    /** Collisions over a view of the level (Stormie's spiders scan through their own chunk view): answers kept for it. */
    public static Object enterCollisions(BlockGetter getter, CollisionContext ctx) {
        Scope previous = SCOPE.get();
        Scope next = null;
        if (!(getter instanceof Level) && ctx instanceof EntityCollisionContext entityCtx && entityCtx.getEntity() != null) {
            next = new Scope(getter, entityCtx.getEntity().level());
        }
        SCOPE.set(next);
        return previous;
    }

    public static void exitCollisions(Object previous) {
        SCOPE.set((Scope) previous);
    }

    public static double offset(BlockGetter world, BlockPos pos, BlockState state, boolean consult, boolean slabbedIdle,
                                Object resolutionContext, Operation<Double> original) {
        if (failed || !slabbedIdle || world == null || pos == null || state == null) {
            return original.call(world, pos, state, consult);
        }
        Level level;
        int view;
        if (world instanceof Level l) {
            level = l;
            view = 0;
        } else {
            Scope scope = SCOPE.get();
            if (scope == null || scope.getter != world) {
                return original.call(world, pos, state, consult);
            }
            level = scope.level;
            view = 1;
        }
        try {
            if (resolutionContext != null && CONTEXT_SIZE.getInt(resolutionContext) != 0) {
                return original.call(world, pos, state, consult);
            }
        } catch (IllegalAccessException | RuntimeException e) {
            failed = true;
            LOG.warn("[Patchwork] slabbedcache: Slabbed's lookup state could not be read, caching off ({})", e.toString());
            return original.call(world, pos, state, consult);
        }
        Table table = TABLES.get();
        long time = level.getGameTime();
        long stamp = CHANGES.get();
        if (table.level != level || table.time != time || table.stamp != stamp || table.size >= MAX_ENTRIES) {
            table.reset(level, time, stamp);
        }
        Long2ObjectOpenHashMap<Entry> map = table.maps[view * 2 + (consult ? 1 : 0)];
        long key = pos.asLong();
        Entry hit = map.get(key);
        if (hit != null && hit.state == state) {
            return hit.value;
        }
        double value = original.call(world, pos, state, consult);
        // A block changed while Slabbed was working this out: keep nothing from the old blocks.
        if (CHANGES.get() == stamp && table.level == level && table.stamp == stamp) {
            if (map.put(key, new Entry(state, value)) == null) table.size++;
        }
        return value;
    }
}
