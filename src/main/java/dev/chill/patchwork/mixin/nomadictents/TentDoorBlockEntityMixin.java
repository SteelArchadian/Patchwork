package dev.chill.patchwork.mixin.nomadictents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import nomadictents.item.MalletItem;
import nomadictents.tileentity.TentDoorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With "enter on collide" on, walking up to a tent door to pack it with a mallet could teleport the player inside
 * first. Players holding a mallet in either hand are not pulled in by touching the door; clicking it still works.
 */
@Mixin(value = TentDoorBlockEntity.class, remap = false)
public abstract class TentDoorBlockEntityMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void patchwork$noEntryWithMallet(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity instanceof Player player && (player.getMainHandItem().getItem() instanceof MalletItem
                || player.getOffhandItem().getItem() instanceof MalletItem)) {
            ci.cancel();
        }
    }
}
