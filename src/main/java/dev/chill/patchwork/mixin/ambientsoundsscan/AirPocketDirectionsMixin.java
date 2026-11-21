package dev.chill.patchwork.mixin.ambientsoundsscan;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * AmbientSounds' air pocket scan calls Direction.values() (a fresh array copy) twice per step of its neighbour loop.
 * The loop only reads it, so one shared copy does.
 */
@Mixin(targets = "team.creative.ambientsounds.environment.pocket.AirPocketScanner", remap = false)
public abstract class AirPocketDirectionsMixin {
    @Unique
    private static final Direction[] patchwork$DIRECTIONS = Direction.values();

    @WrapOperation(method = "scan", require = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;"))
    private Direction[] patchwork$sharedDirections(Operation<Direction[]> original) {
        return patchwork$DIRECTIONS;
    }
}
