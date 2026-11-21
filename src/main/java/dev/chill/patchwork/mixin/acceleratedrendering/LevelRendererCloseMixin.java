package dev.chill.patchwork.mixin.acceleratedrendering;

import dev.chill.patchwork.acceleratedrendering.SecondaryCloseGuard;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets LevelRenderer.close so Accelerated Rendering's close hooks skip freeing shared state for secondary renderers.
 * High priority: applied after AR's mixins, so this TAIL callback runs after AR's (which are guarded by CoreFeature.isLoaded).
 */
@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererCloseMixin {

    @Inject(method = "close", at = @At("HEAD"))
    private void patchwork$closeEnter(CallbackInfo ci) {
        SecondaryCloseGuard.enter((LevelRenderer) (Object) this);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void patchwork$closeExit(CallbackInfo ci) {
        SecondaryCloseGuard.exit();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void patchwork$closeSafety(CallbackInfo ci) {
        SecondaryCloseGuard.exit();
    }
}
