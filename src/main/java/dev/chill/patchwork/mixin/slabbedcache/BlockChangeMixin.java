package dev.chill.patchwork.mixin.slabbedcache;

import dev.chill.patchwork.slabbedcache.OffsetCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Any block change drops the kept Slabbed offsets (before and after, so nothing reads a half-made change). */
@Mixin(LevelChunk.class)
public abstract class BlockChangeMixin {
    @Inject(method = "setBlockState", at = {@At("HEAD"), @At("RETURN")})
    private void patchwork$offsetsChanged(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        OffsetCache.blockChanged();
    }
}
