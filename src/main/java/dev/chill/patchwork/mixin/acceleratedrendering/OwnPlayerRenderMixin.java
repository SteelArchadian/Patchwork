package dev.chill.patchwork.mixin.acceleratedrendering;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chill.patchwork.acceleratedrendering.OwnPlayerVanilla;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your own first-person body (First-person Model) skips Accelerated Rendering's cached meshes; see OwnPlayerVanilla. */
@Mixin(EntityRenderDispatcher.class)
public class OwnPlayerRenderMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void patchwork$ownPlayerEnter(E entity, double x, double y, double z, float yaw, float partialTick,
                                                             PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        OwnPlayerVanilla.enter(OwnPlayerVanilla.isOwnFirstPerson(entity));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void patchwork$ownPlayerExit(E entity, double x, double y, double z, float yaw, float partialTick,
                                                            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (OwnPlayerVanilla.isOwnFirstPerson(entity)) OwnPlayerVanilla.exit();
    }
}
