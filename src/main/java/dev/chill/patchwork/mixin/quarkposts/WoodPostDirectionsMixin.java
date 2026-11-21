package dev.chill.patchwork.mixin.quarkposts;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Quark's wood post getShape loops over Direction.values() twice per shape query, and values() copies the array each
 * time (1.8% of in-world allocation on a low-end laptop). The loops only read it, so one shared copy does.
 */
@Mixin(targets = "org.violetmoon.quark.content.building.block.WoodPostBlock", remap = false)
public abstract class WoodPostDirectionsMixin {
    @Unique
    private static final Direction[] patchwork$DIRECTIONS = Direction.values();

    @WrapOperation(method = "getShape", require = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;"))
    private Direction[] patchwork$sharedDirections(Operation<Direction[]> original) {
        return patchwork$DIRECTIONS;
    }
}
