package dev.chill.patchwork.mixin.signtext;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chill.patchwork.PatchworkSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sign text is drawn glyph by glyph at any distance, even where it is a few unreadable pixels (Shaders profile: 19%
 * of render-thread CPU in a field of signs). Beyond the configured distance only the sign board is drawn. Looking
 * through a spyglass still shows far text, like vanilla's glow-ink outline rule. Hanging signs share this method.
 */
@Mixin(SignRenderer.class)
public abstract class SignRendererMixin {

    @Inject(method = "renderSignText(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/SignText;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIIZ)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void patchwork$skipFarText(BlockPos pos, SignText text, PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, int lineHeight, int maxWidth, boolean isFrontText, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.options.getCameraType().isFirstPerson() && mc.player.isScoping()) {
            return;
        }
        double max = PatchworkSpec.SIGN_TEXT_DISTANCE.getAsInt();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(Vec3.atCenterOf(pos)) > max * max) {
            ci.cancel();
        }
    }
}
