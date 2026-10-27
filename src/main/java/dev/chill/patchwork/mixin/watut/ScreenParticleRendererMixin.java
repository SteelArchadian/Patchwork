package dev.chill.patchwork.mixin.watut;

import com.corosus.watut.client.screen.ScreenParticleRenderer;
import com.corosus.watut.config.ConfigServerControlledSyncedToClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A server config sync makes WATUT resize its small preview framebuffer, which throws if the framebuffer was never
 * created yet. Before setup only the target size is noted (setup creates the buffers at that size); after setup
 * WATUT's own resize runs unchanged.
 */
@Mixin(value = ScreenParticleRenderer.class, remap = false)
public abstract class ScreenParticleRendererMixin {
    @Inject(method = "resizeScaledDown(II)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void patchwork$beforeSetup(int screenWidth, int screenHeight, CallbackInfo ci) {
        ScreenParticleRenderer renderer = (ScreenParticleRenderer) (Object) this;
        if (renderer.getMainRenderTargetScaledDown() != null) {
            return;
        }
        if (ConfigServerControlledSyncedToClient.dynamicGuiShowClientsEntireScreen) {
            renderer.widthScaledDown = screenWidth;
            renderer.heightScaledDown = screenHeight;
        } else {
            renderer.widthScaledDown = ScreenParticleRenderer.defaultWidthScaledDown;
            renderer.heightScaledDown = ScreenParticleRenderer.defaultHeightScaledDown;
        }
        ci.cancel();
    }
}
