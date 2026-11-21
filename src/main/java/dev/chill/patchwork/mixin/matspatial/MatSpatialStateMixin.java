package dev.chill.patchwork.mixin.matspatial;

import dev.chill.patchwork.matspatial.SpatialCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While Spatial GUI shows the inventory on its in-world panel, Inventory Expanded's "real world under the mat" mode
 * would render the whole level again from inside that capture (Spatial GUI's camera and world hooks fire a second
 * time), which drew stray terrain over the screen and left the panel empty. In that case the mat uses its normal
 * stage scene, the same one chests and crafting tables use. Without Spatial GUI nothing changes.
 */
@Pseudo
@Mixin(targets = "com.example.matinventory.render.MatSceneState", remap = false)
public abstract class MatSpatialStateMixin {
    @Inject(method = "actualWorld", at = @At("HEAD"), cancellable = true, require = 1)
    private void patchwork$stageInSpatial(CallbackInfoReturnable<Boolean> cir) {
        if (SpatialCapture.active()) cir.setReturnValue(false);
    }
}
