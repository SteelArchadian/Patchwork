package dev.chill.patchwork.mixin.c2mechunkfutures;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

/**
 * C2ME's chunk holder answers getTickingChunkFuture / getEntityTickingChunkFuture / getFullChunkFuture for a loaded
 * chunk by building a new ChunkResult.of(chunk) and a new CompletableFuture.completedFuture(...) on every call. Mods
 * that ask every holder every tick (Snow Under Trees, Load My Chunks) turn that into about 9% of all server-thread
 * allocation. The answer only depends on which LevelChunk the holder has, so the last completed future is kept and
 * handed out again while the chunk object is the same one; a different chunk builds a fresh one as before.
 */
@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.NewChunkHolderVanillaInterface", remap = false)
public abstract class ChunkFutureReuseMixin {
    @Unique
    private volatile CompletableFuture<ChunkResult<LevelChunk>> patchwork$lastChunkFuture;

    @WrapOperation(method = "wrapOptionalWorldChunkFuture", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkResult;of(Ljava/lang/Object;)Lnet/minecraft/server/level/ChunkResult;"))
    private ChunkResult<?> patchwork$reuseResult(Object chunk, Operation<ChunkResult<?>> original) {
        CompletableFuture<ChunkResult<LevelChunk>> last = patchwork$lastChunkFuture;
        if (last != null) {
            ChunkResult<LevelChunk> result = last.getNow(null);
            if (result != null && result.orElse(null) == chunk) {
                return result;
            }
        }
        return original.call(chunk);
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "wrapOptionalWorldChunkFuture", require = 1, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;completedFuture(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> patchwork$reuseFuture(Object result, Operation<CompletableFuture<?>> original) {
        CompletableFuture<ChunkResult<LevelChunk>> last = patchwork$lastChunkFuture;
        if (last != null && last.getNow(null) == result) {
            return last;
        }
        CompletableFuture<ChunkResult<LevelChunk>> fresh = (CompletableFuture<ChunkResult<LevelChunk>>) original.call(result);
        patchwork$lastChunkFuture = fresh;
        return fresh;
    }
}
