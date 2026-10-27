package dev.chill.patchwork.mixin.loadmychunks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Load My Chunks 1.2.5.1 ticks entities by walking ChunkMap's live updatingChunkMap (a fastutil
 * Long2ObjectLinkedOpenHashMap) and running the tick for each holder while it walks. Ticking entities can load or
 * unload chunks, which changes that same map mid-walk; fastutil's linked-map iterator doesn't detect that and can
 * end up circling forever. The server thread then spins in iterateChunks, the world freezes, and the game hangs on
 * exit waiting for the server. This walks a snapshot of the holders instead: the same holders, in the same order,
 * as at the start of the walk.
 */
@Mixin(targets = "com.drathonix.loadmychunks.common.registry.custom.LoadedChunkProviders$1", remap = false)
public abstract class LoadedChunkProvidersMixin {

    @WrapOperation(method = "iterateChunks", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"))
    private ObjectCollection<?> patchwork$snapshot(Long2ObjectLinkedOpenHashMap<?> map, Operation<ObjectCollection<?>> original) {
        return new ObjectArrayList<>(original.call(map));
    }
}
