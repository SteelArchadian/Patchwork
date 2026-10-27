package dev.chill.patchwork.watut;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.chill.patchwork.Patchwork;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Patchwork's own two shaders for the preview image (assets/patchwork/shaders/core/watut_preview_*):
 * <ul>
 *     <li>downsample: averages each output pixel's footprint of the full-size capture, optional horizontal blur;</li>
 *     <li>mask: optional vertical blur and the circular fade around the edge.</li>
 * </ul>
 * Blur uses binomial weights: 5 taps at level 1, 9 taps at level 2.
 */
final class PreviewShaders {
    private PreviewShaders() {}

    static ShaderInstance downsample;
    static ShaderInstance mask;

    static void register(RegisterShadersEvent event) {
        load(event, "watut_preview_downsample", shader -> downsample = shader);
        load(event, "watut_preview_mask", shader -> mask = shader);
    }

    private static void load(RegisterShadersEvent event, String name, Consumer<ShaderInstance> sink) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(Patchwork.MOD_ID, name), DefaultVertexFormat.POSITION_TEX), sink);
        } catch (IOException e) {
            Patchwork.LOG.warn("[Patchwork/watut] could not load shader {}, the in-world preview stays off", name, e);
        }
    }

    static boolean ready() {
        return downsample != null && mask != null;
    }

    /**
     * Fills the output rectangle (0,0)-(width,height) with the given texture region.
     *
     * @param flipV sample the region bottom-up (WATUT's capture target is stored upside down relative to the output)
     */
    static void draw(ShaderInstance shader, int texture, Matrix4f pose, float width, float height,
                     float u0, float u1, float v0, float v1, boolean flipV) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(() -> shader);
        float vAtTop = flipV ? v1 : v0;
        float vAtBottom = flipV ? v0 : v1;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(pose, 0, 0, 0).setUv(u0, vAtTop);
        buffer.addVertex(pose, 0, height, 0).setUv(u0, vAtBottom);
        buffer.addVertex(pose, width, height, 0).setUv(u1, vAtBottom);
        buffer.addVertex(pose, width, 0, 0).setUv(u1, vAtTop);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
