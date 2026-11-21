package dev.chill.patchwork.mixin.clienttweaksshapes;

import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same as ChainBuildingSupportMixin for panes and bars: anything that isn't a CrossCollisionBlock always gets null
 * (no change), so it is returned before the held item is read.
 */
@Mixin(targets = "net.blay09.mods.clienttweaks.tweak.PaneBuildingSupport", remap = false)
public abstract class PaneBuildingSupportMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true, require = 1)
    private static void patchwork$onlyPanes(BlockState state, CollisionContext context, VoxelShape shape,
                                            CallbackInfoReturnable<VoxelShape> cir) {
        if (state == null || !(state.getBlock() instanceof CrossCollisionBlock)) {
            cir.setReturnValue(null);
        }
    }
}
