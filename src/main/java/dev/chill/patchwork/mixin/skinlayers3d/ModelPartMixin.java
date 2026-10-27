package dev.chill.patchwork.mixin.skinlayers3d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.chill.patchwork.iris.ShadowPass;
import dev.tr7zw.skinlayers.accessor.ModelPartInjector;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * The other half of {@link CustomLayerFeatureRendererMixin}: 3D Skin Layers also hangs voxel meshes on the player's
 * hat/jacket/sleeve ModelParts and draws them from its own ModelPart.render hook. In the shadow pass such a part draws
 * its flat vanilla cubes instead. Priority below 3D Skin Layers' own (300) so this callback runs before its handler.
 */
@Mixin(value = ModelPart.class, priority = 100)
public abstract class ModelPartMixin {
    @Shadow public boolean visible;
    @Shadow public boolean skipDraw;
    @Shadow @Final private Map<String, ModelPart> children;

    @Shadow public abstract void translateAndRotate(PoseStack poseStack);

    @Shadow protected abstract void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color);

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"), cancellable = true)
    private void patchwork$flatInShadow(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color,
                                       CallbackInfo ci) {
        if (!((Object) this instanceof ModelPartInjector part) || part.getInjectedMesh() == null || !ShadowPass.active()) {
            return;
        }
        ci.cancel();
        if (!visible) {
            return;
        }
        poseStack.pushPose();
        translateAndRotate(poseStack);
        if (!skipDraw) {
            compile(poseStack.last(), consumer, light, overlay, color);
        }
        for (ModelPart child : children.values()) {
            child.render(poseStack, consumer, light, overlay, color);
        }
        poseStack.popPose();
    }
}
