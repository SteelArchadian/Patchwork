package dev.chill.patchwork.mixin.c2megetchunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.chill.patchwork.c2megetchunk.MainThreadChunkCache;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every Level.getChunk variant (Lithium's too) ends here. On the server main thread a chunk already in the chunk
 * cache's last-4 list is returned straight away. C2ME's getChunk wrapper (new Object[] plus boxed ints per call) and
 * C2ME's rewrite hook (new CallbackInfoReturnable per call) do nothing else on the main thread.
 */
@Mixin(Level.class)
public abstract class LevelGetChunkMixin {
    @WrapOperation(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkSource;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"),
            require = 0)
    private ChunkAccess patchwork$cachedChunk(ChunkSource source, int x, int z, ChunkStatus status, boolean load,
                                              Operation<ChunkAccess> original) {
        if (source instanceof ServerChunkCache cache) {
            ChunkAccess hit = MainThreadChunkCache.get(cache, x, z, status);
            if (hit != null) {
                return hit;
            }
        }
        return original.call(source, x, z, status, load);
    }
}
