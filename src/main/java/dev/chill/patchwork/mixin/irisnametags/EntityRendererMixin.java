package dev.chill.patchwork.mixin.irisnametags;

import dev.chill.patchwork.iris.ShadowPass;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With a shader pack, Iris draws entities again for the shadow map, name tags included: the tag casts a floating
 * shadow and its text (plus Essential's indicator) costs ~12% of a player's shadow draw. Skip tags in that pass.
 * Low priority so this runs before other mods' name-tag callbacks.
 */
@Mixin(value = EntityRenderer.class, priority = 100)
public abstract class EntityRendererMixin {

    @Inject(method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void patchwork$noTagInShadow(CallbackInfo ci) {
        if (ShadowPass.active()) {
            ci.cancel();
        }
    }
}
