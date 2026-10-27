package dev.chill.patchwork.watut;

import com.corosus.watut.PlayerStatus;
import com.corosus.watut.PlayerStatusManagerClient;
import com.corosus.watut.client.screen.ScreenData;
import dev.chill.patchwork.Patchwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

/**
 * Patchwork's {@code watut} module: What Are They Up To's in-world preview of another player's open screen, made live,
 * sharper and safe. The mixins in {@code mixin.watut} route WATUT's capture, upload, send and receive steps here:
 * <ul>
 *     <li>{@link GuiCapture}: flushes GUI batching around the capture (WATUT's flicker), area-downsamples with our own
 *     shaders, reads back asynchronously;</li>
 *     <li>{@link FrameCodec}: XOR-delta frames, unchanged frames skipped, every network size validated;</li>
 *     <li>{@link FrameTransport}: chunked send and in-order reassembly over WATUT's own packet;</li>
 *     <li>{@link FrameUploader}: straight-to-GPU upload with mipmaps.</li>
 * </ul>
 * Everything else in WATUT runs as shipped. Only reached when the module is active (gated in PatchworkClient).
 */
public final class WatutLivePreview {
    private WatutLivePreview() {}

    /** Counters for {@code -Dpatchwork.verify.watut=true}. Render thread only. */
    static final class Stats {
        static long captured, unchanged, keyframes, deltas, bytes, decoded, dropped;
        private static long ticks;

        static void maybeLog() {
            if (!FrameCodec.VERIFY || ++ticks % 200 != 0 || captured + decoded + dropped == 0) {
                return;
            }
            long sent = keyframes + deltas;
            Patchwork.LOG.info("[Patchwork/watut verify] captured {}, unchanged {}, sent {} key + {} delta frames ({} bytes avg), decoded {}, dropped {}",
                    captured, unchanged, keyframes, deltas, sent == 0 ? 0 : bytes / sent, decoded, dropped);
        }
    }

    private static Level lastLevel;

    public static void init(IEventBus modBus) {
        modBus.addListener(PreviewShaders::register);
    }

    // ---- hooks called by the mixins ----

    public static void captureEnd(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        GuiCapture.onScreenRendered(gui, mouseX, mouseY, partialTick);
    }

    public static void uploadReceivedFrames() {
        FrameUploader.uploadPending();
    }

    public static void receive(PlayerStatusManagerClient manager, UUID sender, CompoundTag tag) {
        FrameTransport.receive(manager, sender, tag);
    }

    /** Before WATUT's client tick. */
    public static void onTickStart(PlayerStatusManagerClient manager) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (mc.level != lastLevel) {
            //new dimension or world: nobody watching has our previous frame
            lastLevel = mc.level;
            GuiCapture.ENCODER.requestKeyframe();
        }
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            //WATUT drops departed players this tick but never frees their texture or pixel buffer
            for (Map.Entry<UUID, PlayerStatus> entry : manager.lookupPlayerToStatus.entrySet()) {
                if (connection.getPlayerInfo(entry.getKey()) == null) {
                    free(entry.getValue().getScreenData());
                }
            }
        }
        if (mc.screen == null) {
            GuiCapture.READBACK.discard();
        }
        Stats.maybeLog();
    }

    /** WATUT's "time for a new image" check: a new screen or a newly arrived player needs a full frame. */
    public static boolean nearbyCheckOnCapture(PlayerStatusManagerClient manager) {
        boolean someoneNew = manager.updateNearbyPlayerListAndCheckIfNewPlayerNear();
        boolean newScreen = manager.getStatusLocal().getScreenData().getLastScreen() != Minecraft.getInstance().screen;
        if (someoneNew || newScreen) {
            GuiCapture.ENCODER.requestKeyframe();
        }
        return someoneNew;
    }

    /**
     * WATUT's "resend the last image to a newcomer" check. Our last image may be a delta the newcomer can't use, so ask
     * for a fresh full capture instead and tell WATUT there is nothing to resend.
     */
    public static boolean nearbyCheckOnResend(PlayerStatusManagerClient manager) {
        if (manager.updateNearbyPlayerListAndCheckIfNewPlayerNear()) {
            Minecraft mc = Minecraft.getInstance();
            ScreenData mine = manager.getStatusLocal().getScreenData();
            GuiCapture.ENCODER.requestKeyframe();
            mine.setGameTicksSinceLastScreenSend(mc.level != null ? mc.level.getGameTime() : 0);
            mine.setNeedsNewRenderToPixelData(true);
        }
        return false;
    }

    private static void free(ScreenData screen) {
        screen.closeImage();
        ByteBuffer pixels = screen.getDecompressionBuffer();
        if (pixels != null) {
            screen.setDecompressionBuffer(null);
            MemoryUtil.memFree(pixels);
        }
        screen.setTexturePixelData(null);
        screen.getIsBufferReady().set(false);
        FrameTransport.forget(screen);
    }
}
