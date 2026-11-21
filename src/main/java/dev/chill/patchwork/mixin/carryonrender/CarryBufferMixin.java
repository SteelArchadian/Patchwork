package dev.chill.patchwork.mixin.carryonrender;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.SequencedMap;

/**
 * Carry On drew carried objects through a buffer of its own with the depth test off and blending on, so they showed
 * through walls, and shader packs (which never saw that buffer) drew them see-through. They now go through the
 * world's own buffers with the depth test on, like any model, at their normal size. Carry On's own flushes of those
 * buffers still run.
 */
@Mixin(targets = "tschipp.carryon.client.render.CarriedObjectRender", remap = false)
public abstract class CarryBufferMixin {
    @Redirect(method = "drawThirdPerson", require = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;immediateWithBuffers(Ljava/util/SequencedMap;Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;)Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"))
    private static MultiBufferSource.BufferSource patchwork$worldBuffers(SequencedMap<?, ByteBufferBuilder> builders, ByteBufferBuilder fallback) {
        return Minecraft.getInstance().renderBuffers().bufferSource();
    }

    @Redirect(method = "drawThirdPerson", require = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableDepthTest()V"))
    private static void patchwork$keepDepthTest() {
    }

    @Redirect(method = "drawThirdPerson", require = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;enableBlend()V"))
    private static void patchwork$noBlend() {
    }
}
