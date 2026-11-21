package dev.chill.patchwork.mixin.emfdefer;

import dev.chill.patchwork.emfdefer.EmfDefer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.models.animation.EMFAnimationHandler;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/** Records which model variants get animations, for the normal-vs-verify digest. */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
public class EMFModelPartRootMixin {
    @Inject(method = "receiveAnimationHandler", at = @At("HEAD"), require = 1)
    private void patchwork$received(int variant, EMFAnimationHandler handler, CallbackInfo ci) {
        EmfDefer.received((EMFModelPartRoot) (Object) this, variant);
    }
}
