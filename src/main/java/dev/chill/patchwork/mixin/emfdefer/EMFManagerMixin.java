package dev.chill.patchwork.mixin.emfdefer;

import dev.chill.patchwork.emfdefer.EmfDefer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/** Queues EMF's animation parsing while the loading screen is up (see {@link EmfDefer}). */
@Pseudo
@Mixin(targets = "traben.entity_model_features.EMFManager", remap = false)
public class EMFManagerMixin {
    @Inject(method = "setupAnimationsFromJemToModel", at = @At("HEAD"), cancellable = true, require = 1)
    private void patchwork$defer(EMFJemData jemData, EMFModelPartRoot emfRootPart, int variantNum, CallbackInfo ci) {
        if (EmfDefer.defer(jemData, emfRootPart, variantNum)) ci.cancel();
    }

    @Inject(method = "resetInstance", at = @At("HEAD"), require = 1)
    private static void patchwork$reset(CallbackInfo ci) {
        EmfDefer.reset();
    }
}
