package dev.chill.patchwork.mixin.skinlayers3d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.chill.patchwork.iris.ShadowPass;
import dev.tr7zw.skinlayers.renderlayers.CustomLayerFeatureRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3D Skin Layers 1.11.3 builds its voxel outer layer for the shadow pass as well, roughly doubling a player's shadow
 * cost (Shaders chat A/B: 0.95 ms vs 0.42 ms "Shadow Entities"). In the shadow pass the flat vanilla outer layer is
 * drawn instead (a few quads), so the shadow keeps its outline. The main pass is untouched.
 */
@Mixin(value = CustomLayerFeatureRenderer.class, remap = false)
public abstract class CustomLayerFeatureRendererMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void patchwork$flatInShadow(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                       float limbAngle, float limbDistance, float tickDelta, float age, float headYaw,
                                       float headPitch, CallbackInfo ci) {
        if (!ShadowPass.active()) {
            return;
        }
        ci.cancel();
        if (player.isInvisible()) {
            return;
        }
        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> model = ((RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) this).getParentModel();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture(), true));
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        drawFlat(model.hat, PlayerModelPart.HAT, player, poseStack, consumer, light, overlay);
        drawFlat(model.jacket, PlayerModelPart.JACKET, player, poseStack, consumer, light, overlay);
        drawFlat(model.leftSleeve, PlayerModelPart.LEFT_SLEEVE, player, poseStack, consumer, light, overlay);
        drawFlat(model.rightSleeve, PlayerModelPart.RIGHT_SLEEVE, player, poseStack, consumer, light, overlay);
        drawFlat(model.leftPants, PlayerModelPart.LEFT_PANTS_LEG, player, poseStack, consumer, light, overlay);
        drawFlat(model.rightPants, PlayerModelPart.RIGHT_PANTS_LEG, player, poseStack, consumer, light, overlay);
    }

    /** Draws a vanilla outer part that 3D Skin Layers hid (visible parts were already drawn with the model). */
    private static void drawFlat(ModelPart part, PlayerModelPart which, AbstractClientPlayer player, PoseStack poseStack,
                                 VertexConsumer consumer, int light, int overlay) {
        if (part.visible || !player.isModelPartShown(which)) {
            return;
        }
        part.visible = true;
        try {
            part.render(poseStack, consumer, light, overlay);
        } finally {
            part.visible = false;
        }
    }
}
