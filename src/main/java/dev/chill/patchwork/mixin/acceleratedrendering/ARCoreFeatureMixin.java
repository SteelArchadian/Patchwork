package dev.chill.patchwork.mixin.acceleratedrendering;

import dev.chill.patchwork.acceleratedrendering.ShaderTranslucentGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Accelerated Rendering's "force translucent acceleration" batches translucent models (players, piglins, ...) into its own
 * buffers, which are drawn outside the translucent phase a shader pack expects; with Iris the sky ends up composited over
 * those heads. While the world is rendered with a shader pack active, translucent models take the normal path instead.
 * Opaque models, the GUI, and rendering without shaders stay accelerated exactly as configured.
 */
@Pseudo
@Mixin(targets = "com.github.argon4w.acceleratedrendering.core.CoreFeature", remap = false)
public class ARCoreFeatureMixin {

    @Inject(method = "shouldForceAccelerateTranslucent", at = @At("RETURN"), cancellable = true)
    private static void patchwork$notUnderShaders(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ShaderTranslucentGuard.blockForcedTranslucent()) {
            cir.setReturnValue(false);
        }
    }
}
