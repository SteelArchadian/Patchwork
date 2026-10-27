package dev.chill.patchwork.mixin.supplementaries;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mehvahdjukaar.moonlight.api.set.BlockType;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodTypeRegistry;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Supplementaries 3.9.9's wrench (and turntable) rotation asks Moonlight which wood type a block belongs to whenever
 * Quark is loaded. For a block with no inventory item (cave vines, piston heads, ...) Moonlight throws instead of
 * answering, so clicking the side of hanging cave vines with a wrench crashes. Such a block is never a wood block, so
 * it gets "no wood type", which the following code already handles; every other block is looked up as before.
 */
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.utils.BlockUtil", remap = false)
public abstract class BlockUtilMixin {

    @WrapOperation(method = "getRotatedState", at = @At(value = "INVOKE",
            target = "Lnet/mehvahdjukaar/moonlight/api/set/wood/WoodTypeRegistry;getBlockTypeOf(Lnet/minecraft/world/level/ItemLike;)Lnet/mehvahdjukaar/moonlight/api/set/BlockType;"))
    private static BlockType patchwork$skipItemless(WoodTypeRegistry registry, ItemLike block, Operation<BlockType> original) {
        if (block instanceof Block b && b.asItem() == Items.AIR) {
            return null;
        }
        return original.call(registry, block);
    }
}
