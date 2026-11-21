package dev.chill.patchwork.mixin.clienttweaksshapes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client Tweaks runs this from its BlockStateBase.getShape hook for every block's shape query, and it reads the
 * player's held item before it checks whether the block is a chain at all. For anything but a chain the answer is
 * always null (no change), so that is returned first.
 */
@Mixin(targets = "net.blay09.mods.clienttweaks.tweak.ChainBuildingSupport", remap = false)
public abstract class ChainBuildingSupportMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true, require = 1)
    private static void patchwork$onlyChains(BlockState state, BlockPos pos, CollisionContext context, VoxelShape shape,
                                             CallbackInfoReturnable<VoxelShape> cir) {
        if (state == null || !(state.getBlock() instanceof ChainBlock)) {
            cir.setReturnValue(null);
        }
    }
}
