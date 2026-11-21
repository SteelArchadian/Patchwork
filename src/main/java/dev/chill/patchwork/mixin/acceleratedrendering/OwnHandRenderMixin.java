package dev.chill.patchwork.mixin.acceleratedrendering;

import dev.chill.patchwork.acceleratedrendering.OwnPlayerVanilla;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla first-person arm (shown for maps and other auto-vanilla-hand items) gets the same normal path. */
@Mixin(ItemInHandRenderer.class)
public class OwnHandRenderMixin {

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void patchwork$armEnter(CallbackInfo ci) {
        OwnPlayerVanilla.enter(true);
    }

    @Inject(method = "renderPlayerArm", at = @At("RETURN"))
    private void patchwork$armExit(CallbackInfo ci) {
        OwnPlayerVanilla.exit();
    }
}
