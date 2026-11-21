package dev.chill.patchwork.srspawnwarm;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Streams Reflowing builds its river engine (and warms the regions around the world origin) only when world
 * generation first asks for it, which is after vanilla has spent 10-15 s placing the stronghold rings on one thread.
 * Spawn generation then waits on the river samplers while the chunk workers sit idle. This asks for the same work
 * through the mod's own teleport prefetch ({@code StreamPrefetcher.submitUrgentAt}) as soon as the overworld exists,
 * and again at the real spawn once it is known, on a short-lived thread so the server thread never waits on it.
 * Same rivers: it only starts the mod's own work earlier.
 */
public final class SpawnWarm {
    private static final Logger LOG = LogUtils.getLogger();
    private static final MethodHandle SUBMIT = find();

    private SpawnWarm() {
    }

    private static MethodHandle find() {
        try {
            Class<?> prefetcher = Class.forName("dev.streamsreflowing.worldgen.StreamPrefetcher");
            return MethodHandles.publicLookup().findStatic(prefetcher, "submitUrgentAt",
                    MethodType.methodType(void.class, ServerLevel.class, int.class, int.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("[Patchwork/srspawnwarm] Streams Reflowing prefetch not found; spawn warm-up off", e);
            return null;
        }
    }

    public static void warm(ServerLevel level, String reason) {
        if (SUBMIT == null || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX();
        int z = spawn.getZ();
        Thread thread = new Thread(() -> {
            long start = System.nanoTime();
            try {
                SUBMIT.invoke(level, x, z);
                LOG.info("[Patchwork/srspawnwarm] river prefetch queued at {},{} ({}) in {} ms", x, z, reason,
                        (System.nanoTime() - start) / 1_000_000);
            } catch (Throwable t) {
                LOG.warn("[Patchwork/srspawnwarm] river prefetch at {},{} failed (ignored)", x, z, t);
            }
        }, "patchwork-sr-spawn-warm");
        thread.setDaemon(true);
        thread.start();
    }
}
