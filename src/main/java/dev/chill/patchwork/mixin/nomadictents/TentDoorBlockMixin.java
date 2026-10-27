package dev.chill.patchwork.mixin.nomadictents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import nomadictents.block.TentBlock;
import nomadictents.block.TentDoorBlock;
import nomadictents.tileentity.TentDoorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Nomadic Tents 21.1.0 handles door clicks in {@code useWithoutItem} and passes {@code player.getUsedItemHand()} on,
 * which is the hand of the last item use (eating, drawing a bow), not the hand that clicked. So a mallet click was
 * often read as the other hand and the player was sent into the tent instead of packing it up (upstream issue #99).
 * The door now handles the click in {@code useItemOn}, which is given the clicking hand; the rest is the mod's own code.
 */
@Mixin(value = TentDoorBlock.class, remap = false)
public abstract class TentDoorBlockMixin extends TentBlock {

    private TentDoorBlockMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockPos door = state.getValue(TentDoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level.getBlockEntity(door) instanceof TentDoorBlockEntity entity) {
            entity.use(level.getBlockState(door), level, door, player, hand);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** Fallback path (only reached if another mod passes the click on): the main hand, as for any plain click. */
    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"))
    private InteractionHand patchwork$clickHand(Player player) {
        return InteractionHand.MAIN_HAND;
    }
}
