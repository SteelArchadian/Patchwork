package dev.chill.patchwork.c2megetchunk;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * Reads the small "last 4 chunks" cache ServerChunkCache keeps for its main thread: Lithium's (cacheKeys/cacheChunks)
 * when Lithium put it there, else vanilla's (lastChunkPos/lastChunkStatus/lastChunk). A hit is exactly what getChunk
 * itself returns on the main thread, so the caller can skip the full call (and C2ME's per-call wrapper garbage).
 * If the fields can't be found the fast path is off and getChunk runs as normal.
 */
public final class MainThreadChunkCache {
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    private static final MethodHandle MAIN_THREAD;
    private static final MethodHandle LITHIUM_KEYS;
    private static final MethodHandle LITHIUM_CHUNKS;
    private static final MethodHandle VANILLA_POS;
    private static final MethodHandle VANILLA_STATUS;
    private static final MethodHandle VANILLA_CHUNKS;
    private static final boolean LITHIUM;
    private static final boolean ON;

    static {
        MethodHandle main = null, lk = null, lc = null, vp = null, vs = null, vc = null;
        boolean lithium = false, on = false;
        try {
            main = getter("mainThread");
            lk = optionalGetter("cacheKeys", long[].class);
            lc = optionalGetter("cacheChunks", ChunkAccess[].class);
            lithium = lk != null && lc != null;
            if (!lithium) {
                vp = getter("lastChunkPos");
                vs = getter("lastChunkStatus");
                vc = getter("lastChunk");
            }
            on = true;
            LOG.info("[Patchwork] c2megetchunk: main-thread chunk lookups that hit the {} chunk cache skip the full getChunk call",
                    lithium ? "Lithium" : "vanilla");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[Patchwork] c2megetchunk: chunk cache not found, fast path off ({})", e.toString());
        }
        MAIN_THREAD = main;
        LITHIUM_KEYS = lk;
        LITHIUM_CHUNKS = lc;
        VANILLA_POS = vp;
        VANILLA_STATUS = vs;
        VANILLA_CHUNKS = vc;
        LITHIUM = lithium;
        ON = on;
    }

    private MainThreadChunkCache() {
    }

    private static MethodHandle getter(String name) throws ReflectiveOperationException {
        Field f = ServerChunkCache.class.getDeclaredField(name);
        f.setAccessible(true);
        MethodHandle h = MethodHandles.lookup().unreflectGetter(f);
        return h.asType(h.type().changeReturnType(Object.class));
    }

    private static MethodHandle optionalGetter(String name, Class<?> type) throws ReflectiveOperationException {
        for (Field f : ServerChunkCache.class.getDeclaredFields()) {
            if (f.getName().equals(name) && f.getType() == type) {
                return getter(name);
            }
        }
        return null;
    }

    /** The cached chunk for a main-thread lookup, or null to run getChunk as normal. */
    public static ChunkAccess get(ServerChunkCache cache, int x, int z, ChunkStatus status) {
        if (!ON) {
            return null;
        }
        try {
            if (Thread.currentThread() != (Object) MAIN_THREAD.invokeExact(cache)) {
                return null;
            }
            if (LITHIUM) {
                // Lithium's key packs x, z (28 bits each) and the status index, so the status must match too.
                long key = ((long) x & 0xFFFFFFFL) | (((long) z & 0xFFFFFFFL) << 28) | ((long) status.getIndex() << 56);
                long[] keys = (long[]) (Object) LITHIUM_KEYS.invokeExact(cache);
                ChunkAccess[] chunks = (ChunkAccess[]) (Object) LITHIUM_CHUNKS.invokeExact(cache);
                for (int i = 0; i < 4; i++) {
                    if (keys[i] == key) {
                        return chunks[i];
                    }
                }
                return null;
            }
            long pos = ChunkPos.asLong(x, z);
            long[] positions = (long[]) (Object) VANILLA_POS.invokeExact(cache);
            ChunkStatus[] statuses = (ChunkStatus[]) (Object) VANILLA_STATUS.invokeExact(cache);
            ChunkAccess[] chunks = (ChunkAccess[]) (Object) VANILLA_CHUNKS.invokeExact(cache);
            for (int i = 0; i < 4; i++) {
                if (positions[i] == pos && statuses[i] == status) {
                    return chunks[i];
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
