package dev.chill.patchwork.mixin.essential;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes Essential's cosmetics layer from player rendering. Foundation uses Essential only for friends, world
 * hosting and messaging; skins come from the pack's skin manager. Essential runs this layer for every player on
 * every frame (twice with shader shadows) and builds a new set of all body parts before it even checks whether
 * the player wears anything, about 60 MB/s of garbage on the render thread even with cosmetics disabled.
 */
@Pseudo
@Mixin(targets = "gg.essential.cosmetics.EssentialModelRenderer", remap = false)
public abstract class CosmeticsLayerMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void patchwork$skipCosmetics(CallbackInfo ci) {
        ci.cancel();
    }
}
