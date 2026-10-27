package dev.chill.patchwork.mixin.watut;

import dev.chill.patchwork.watut.WatutLivePreview;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces WATUT's gui capture (renderWithTooltipEnd) and received-frame upload (guiRender) with
 * {@link WatutLivePreview}: flicker fix, async readback, delta frames, proper downsampling, validated uploads.
 */
@Mixin(targets = "com.corosus.watut.client.screen.RenderHelper", remap = false)
public abstract class RenderHelperMixin {
    @Inject(method = "renderWithTooltipEnd(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void patchwork$capture(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        WatutLivePreview.captureEnd(guiGraphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }

    @Inject(method = "guiRender(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void patchwork$upload(GuiGraphics guiGraphics, CallbackInfo ci) {
        WatutLivePreview.uploadReceivedFrames();
        ci.cancel();
    }
}
