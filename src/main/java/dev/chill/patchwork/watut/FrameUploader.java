package dev.chill.patchwork.watut;

import com.corosus.watut.PlayerStatus;
import com.corosus.watut.WatutMod;
import com.corosus.watut.client.screen.ScreenData;
import com.corosus.watut.client.screen.ScreenParticleRenderer;
import com.corosus.watut.config.ConfigClient;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Puts decoded preview frames into the textures WATUT draws in the world (spec B2), at most once per
 * {@code tickReceiveAndRenderRateOfGUIUpdates} game ticks per player.
 */
final class FrameUploader {
    private FrameUploader() {}

    private static final int MIP_LEVELS = 4;

    static void uploadPending() {
        Minecraft mc = Minecraft.getInstance();
        long tick = mc.level != null ? mc.level.getGameTime() : 0;
        for (PlayerStatus player : WatutMod.getPlayerStatusManagerClient().lookupPlayerToStatus.values()) {
            ScreenData screen = player.getScreenData();
            if (isDue(screen, tick)) {
                upload(screen, tick);
            }
        }
    }

    private static boolean isDue(ScreenData screen, long tick) {
        return screen.getIsBufferReady().get()
                && screen.needsNewRenderFromPixelData()
                && screen.getTexturePixelData() != null
                && tick - screen.getGameTicksSinceLastScreenReceiveAndRender() > ConfigClient.tickReceiveAndRenderRateOfGUIUpdates;
    }

    private static void upload(ScreenData screen, long tick) {
        screen.getIsBufferReady().set(false);
        screen.markNeedsNewRenderFromPixelData(false);

        int width = screen.getWidth();
        int height = screen.getHeight();
        ByteBuffer pixels = screen.getDecompressionBuffer();
        if (pixels == null || pixels.capacity() < width * height * FrameCodec.BYTES_PER_PIXEL) {
            //the canvas and the size disagree: copying would read past native memory, skip this frame
            return;
        }
        screen.setGameTicksSinceLastScreenReceiveAndRender(tick);

        ScreenParticleRenderer.getInstance().checkSetup();
        if (screen.getParticleRenderType() == null) {
            screen.initClient();
        }
        DynamicTexture texture = textureOfSize(screen, width, height);

        texture.bind();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, MemoryUtil.memAddress0(pixels));

        //a small screen seen from a few blocks away: mips + linear filtering keep it from shimmering
        //(vanilla's texture setup pins max level and max LOD to 0, both need raising for the mips to be used)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, MIP_LEVELS);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, MIP_LEVELS);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    private static DynamicTexture textureOfSize(ScreenData screen, int width, int height) {
        DynamicTexture texture = screen.getImage();
        boolean fits = texture != null && texture.getPixels() != null
                && texture.getPixels().getWidth() == width && texture.getPixels().getHeight() == height;
        if (!fits) {
            screen.closeImage();
            texture = new DynamicTexture(width, height, true);
            screen.setImage(texture);
        }
        return texture;
    }
}
