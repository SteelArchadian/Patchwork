package dev.chill.patchwork.watut;

import com.corosus.watut.WatutMod;
import com.corosus.watut.client.screen.RenderHelper;
import com.corosus.watut.client.screen.ScreenData;
import com.corosus.watut.client.screen.ScreenParticleRenderer;
import com.corosus.watut.config.ConfigServerControlledSyncedToClient;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.chill.patchwork.Patchwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Turns the player's open screen into the small preview image other players see (spec B1).
 *
 * Each request: draw the screen into WATUT's full-size capture target, shrink it into our own target (pass A), fade it
 * into a circle in WATUT's output target (pass B), then copy that back to the CPU asynchronously. The CPU copy is
 * picked up on a later frame and encoded on {@link EncoderThread}; finished frames are sent by {@link FrameTransport}.
 */
final class GuiCapture {
    private GuiCapture() {}

    private record Source(int texture, boolean upright) {}

    private static final int CLEAR = GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT;

    static final FrameCodec.Encoder ENCODER = new FrameCodec.Encoder();
    static final AsyncFrameReader READBACK = new AsyncFrameReader();
    private static EncoderThread encoderThread;
    private static RenderTarget shrunk;
    private static boolean renderFailureLogged;
    private static boolean shaderMissingLogged;

    private static EncoderThread encoderThread() {
        if (encoderThread == null) {
            encoderThread = new EncoderThread(ENCODER);
        }
        return encoderThread;
    }

    /** Called at the end of every screen render. */
    static void onScreenRendered(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!RenderHelper.useDynamicGUISystem() || mc.level == null || mc.player == null) {
            return;
        }
        ScreenData mine = WatutMod.getPlayerStatusManagerClient().getStatusLocal().getScreenData();
        collectFinishedWork(mine);

        if (!mine.isNeedsNewRenderToPixelData() || encoderThread().busy() || READBACK.isPending()) {
            return;
        }
        mine.setNeedsNewRenderToPixelData(false);
        if (!PreviewShaders.ready()) {
            if (!shaderMissingLogged) {
                shaderMissingLogged = true;
                Patchwork.LOG.warn("[Patchwork/watut] preview shaders not loaded, not capturing");
            }
            return;
        }

        ScreenParticleRenderer watut = ScreenParticleRenderer.getInstance();
        watut.checkSetup();
        //anything the real screen queued in GuiGraphics must reach the real screen, not our capture
        gui.flush();
        RenderHelper.unbindVanillaRenderTarget();
        watut.bind();
        RenderSystem.clear(CLEAR, Minecraft.ON_OSX);
        Source source = drawScreen(mc, gui, watut, mouseX, mouseY, partialTick);
        watut.unbind();

        shrinkAndFade(mc, watut, source, gui.pose().last().pose());
        RenderHelper.bindVanillaRenderTargetAndSetupProjectionMatrix();
    }

    private static void collectFinishedWork(ScreenData mine) {
        FrameCodec.RawFrame copied = READBACK.poll();
        if (copied != null) {
            encoderThread().submit(copied);
        }
        FrameCodec.EncodedFrame encoded = encoderThread().poll();
        if (encoded == null) {
            return;
        }
        if (encoded.unchanged()) {
            WatutLivePreview.Stats.unchanged++;
            return;
        }
        //WATUT only offers its "resend to newcomers" path when this is non-null
        mine.setTexturePixelData(ByteBuffer.wrap(encoded.data()));
        FrameTransport.send(encoded);
    }

    private static Source drawScreen(Minecraft mc, GuiGraphics gui, ScreenParticleRenderer watut,
                                     int mouseX, int mouseY, float partialTick) {
        int captureTexture = watut.getMainRenderTarget().getColorTextureId();
        Screen screen = mc.screen;
        if (screen == null) {
            return new Source(captureTexture, false);
        }
        boolean hideBackground = ConfigServerControlledSyncedToClient.dynamicGuiDisableBackgroundRendering;
        ScreenParticleRenderer.isRenderingParticleGUI = hideBackground;
        ScreenParticleRenderer.isRenderingParticleGUI2 = hideBackground;
        RenderHelper.performingOwnRender = true;
        try {
            if (RenderHelper.isXaeroGuiMap(screen)) {
                //Xaero's world map draws into its own framebuffer, use that texture as it is
                int mapTexture = xaeroMapTexture();
                if (mapTexture >= 0) {
                    return new Source(mapTexture, true);
                }
                return new Source(captureTexture, false);
            }
            try {
                screen.renderWithTooltip(gui, mouseX, mouseY, partialTick);
            } catch (RuntimeException e) {
                if (!renderFailureLogged) {
                    renderFailureLogged = true;
                    Patchwork.LOG.warn("[Patchwork/watut] {} failed to draw a second time for the preview, skipping its preview",
                            screen.getClass().getName(), e);
                }
            }
            gui.flush();
            watut.innerBlit(gui.pose(), RenderHelper.cursor, mouseX, mouseX + 6, mouseY, mouseY + 10, 100, 0, 1, 0, 1);
            return new Source(captureTexture, false);
        } finally {
            RenderHelper.performingOwnRender = false;
            ScreenParticleRenderer.isRenderingParticleGUI = false;
            ScreenParticleRenderer.isRenderingParticleGUI2 = false;
        }
    }

    private static int xaeroMapTexture() {
        if (RenderHelper.guiMapPrimaryScaleFBO == null || RenderHelper.colorTextureId == null) {
            return -1;
        }
        try {
            Object framebuffer = RenderHelper.guiMapPrimaryScaleFBO.get(null);
            Object id = framebuffer == null ? null : RenderHelper.colorTextureId.get(framebuffer);
            return id instanceof Integer texture ? texture : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static void shrinkAndFade(Minecraft mc, ScreenParticleRenderer watut, Source source, Matrix4f pose) {
        int outW = watut.widthScaledDown;
        int outH = watut.heightScaledDown;
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, outW, outH, 0, 1000F, ClientHooks.getGuiFarPlane()),
                VertexSorting.ORTHOGRAPHIC_Z);

        //the middle (output size x gui scale) of the capture, so the preview shows the gui at its usual proportions
        float scale = ConfigServerControlledSyncedToClient.dynamicGuiShowClientsEntireScreen ? 1F : (float) mc.getWindow().getGuiScale();
        float halfU = outW * scale / 2F / watut.width;
        float halfV = outH * scale / 2F / watut.height;
        float blur = ConfigServerControlledSyncedToClient.dynamicGuiBlurLevel;

        RenderTarget a = shrunkTarget(outW, outH);
        a.bindWrite(true);
        RenderSystem.clear(CLEAR, Minecraft.ON_OSX);
        PreviewShaders.downsample.safeGetUniform("SampleStep").set(source.upright() ? 1F : scale);
        PreviewShaders.downsample.safeGetUniform("BlurLevel").set(source.upright() ? 0F : blur);
        PreviewShaders.draw(PreviewShaders.downsample, source.texture(), pose, outW, outH,
                0.5F - halfU, 0.5F + halfU, 0.5F - halfV, 0.5F + halfV, !source.upright());
        a.unbindWrite();

        watut.bindScaledDown();
        RenderSystem.clear(CLEAR, Minecraft.ON_OSX);
        PreviewShaders.mask.safeGetUniform("OutputSize").set((float) outW, (float) outH);
        PreviewShaders.mask.safeGetUniform("FadeRadius").set((float) ConfigServerControlledSyncedToClient.dynamicGuiSizeRadiusInPixelsToShow);
        PreviewShaders.mask.safeGetUniform("BlurLevel").set(blur);
        PreviewShaders.draw(PreviewShaders.mask, a.getColorTextureId(), pose, outW, outH, 0, 1, 0, 1, true);

        READBACK.start(outW, outH);
        WatutLivePreview.Stats.captured++;
        watut.unbindScaledDown();
    }

    private static RenderTarget shrunkTarget(int width, int height) {
        if (shrunk == null) {
            shrunk = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            shrunk.setClearColor(0, 0, 0, 0);
        } else if (shrunk.width != width || shrunk.height != height) {
            shrunk.resize(width, height, Minecraft.ON_OSX);
        }
        return shrunk;
    }
}
