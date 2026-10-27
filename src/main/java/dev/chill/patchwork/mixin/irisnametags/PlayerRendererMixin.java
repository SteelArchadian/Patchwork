package dev.chill.patchwork.mixin.irisnametags;

import dev.chill.patchwork.iris.ShadowPass;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PlayerRenderer's override (score line, then the tag) of {@link EntityRendererMixin}'s target. */
@Mixin(value = PlayerRenderer.class, priority = 100)
public abstract class PlayerRendererMixin {

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void patchwork$noTagInShadow(CallbackInfo ci) {
        if (ShadowPass.active()) {
            ci.cancel();
        }
    }
}
