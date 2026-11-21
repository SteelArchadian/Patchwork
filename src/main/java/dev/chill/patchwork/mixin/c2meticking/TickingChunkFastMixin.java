package dev.chill.patchwork.mixin.c2meticking;

import com.ishland.c2me.rewrites.chunksystem.common.ChunkState;
import com.ishland.c2me.rewrites.chunksystem.common.NewChunkStatus;
import com.ishland.flowsched.scheduler.ItemHolder;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;

/**
 * C2ME answers getTickingChunk() by taking the chunk holder's lock, wrapping the block-ticking future in a new one
 * and reading it. Mods that ask every loaded chunk every tick (Load My Chunks; vanilla too) then wait on that lock
 * whenever a C2ME worker is advancing the same chunk: 8-22 ms stalls on the server thread in Test Dummy captures.
 * The same answer is read without the lock: the futures array slot and the chunk item are each safe to read on their
 * own (CompletableFuture and AtomicReference), just as vanilla's ChunkHolder reads its futures. A done future gives
 * the holder's LevelChunk, a pending one gives null, and a failed one takes C2ME's own locked path, unchanged.
 */
@Pseudo
@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.NewChunkHolderVanillaInterface", remap = false)
public abstract class TickingChunkFastMixin {
    @Shadow(remap = false) @Final private ItemHolder<?, ChunkState, ?, ?> newHolder;

    @Shadow(remap = false)
    public abstract CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture();

    /**
     * @author Patchwork
     * @reason same answer without taking the chunk holder's lock or making a future
     */
    @Overwrite(remap = false)
    public LevelChunk getTickingChunk() {
        CompletableFuture<Void> ticking = ((ItemHolderAccessor) (Object) newHolder).patchwork$futures()[NewChunkStatus.BLOCK_TICKING.ordinal()];
        if (!ticking.isDone()) {
            return null;
        }
        if (ticking.isCompletedExceptionally()) {
            return getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).orElse(null);
        }
        ChunkState state = newHolder.getItem().get();
        ChunkAccess chunk = state == null ? null : state.chunk();
        return chunk instanceof LevelChunk level ? level : null;
    }
}
