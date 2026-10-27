package dev.chill.patchwork.watut;

import com.corosus.watut.PlayerStatusManagerClient;
import com.corosus.watut.WatutNetworking;
import com.corosus.watut.client.screen.ScreenData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Gets preview frames to other players through WATUT's own packet (spec B3, B4, B5). WATUT's server relays the tag to
 * nearby players unchanged, so only the client ends need to agree on the format.
 */
final class FrameTransport {
    private FrameTransport() {}

    /** Our two keys; a frame without them comes from stock WATUT and is always a full image. */
    static final String KEY_KEYFRAME = "screenFrameKeyframe";
    static final String KEY_SEQ = "screenFrameSeq";
    private static final int CHUNK_BYTES = 30000;
    private static final int ASSEMBLY_TIMEOUT_TICKS = 10;
    //a full 4096x4096 RGBA frame is 64 MB raw, its deflated form can't sensibly be bigger
    private static final int MAX_ASSEMBLED_BYTES = FrameCodec.MAX_FRAME_DIMENSION * FrameCodec.MAX_FRAME_DIMENSION * FrameCodec.BYTES_PER_PIXEL;

    /** A frame arriving in several packets, per sending player. */
    private static final class Incoming {
        final FrameCodec.ReceiveState codec = new FrameCodec.ReceiveState();
        ByteArrayOutputStream parts;
        int expectedIndex;
        long startedAt;
    }

    private static final Map<ScreenData, Incoming> INCOMING = new WeakHashMap<>();

    static void send(FrameCodec.EncodedFrame frame) {
        byte[] payload = frame.data();
        int chunks = Math.max(1, (payload.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        for (int index = 0; index < chunks; index++) {
            int from = index * CHUNK_BYTES;
            CompoundTag tag = new CompoundTag();
            tag.putInt(WatutNetworking.NBTDataPlayerScreenCompressedPixelDataSize, frame.rawSize());
            tag.putInt(WatutNetworking.NBTDataPlayerScreenWidth, frame.width());
            tag.putInt(WatutNetworking.NBTDataPlayerScreenHeight, frame.height());
            tag.putBoolean(KEY_KEYFRAME, frame.keyframe());
            tag.putInt(KEY_SEQ, frame.seq());
            tag.putByteArray(WatutNetworking.NBTDataPlayerScreenCompressedPixelData,
                    Arrays.copyOfRange(payload, from, Math.min(payload.length, from + CHUNK_BYTES)));
            tag.putInt(WatutNetworking.NBTDataPlayerScreenCompressedPixelDataPacketCount, chunks);
            tag.putInt(WatutNetworking.NBTDataPlayerScreenCompressedPixelDataPacketIndex, index);
            WatutNetworking.instance().clientSendToServer(tag);
        }
        if (frame.keyframe()) {
            WatutLivePreview.Stats.keyframes++;
        } else {
            WatutLivePreview.Stats.deltas++;
        }
        WatutLivePreview.Stats.bytes += payload.length;
    }

    /** Takes the image part out of a WATUT packet (removing it from the tag) and decodes it when complete. */
    static void receive(PlayerStatusManagerClient manager, UUID sender, CompoundTag tag) {
        String dataKey = WatutNetworking.NBTDataPlayerScreenCompressedPixelData;
        if (!tag.contains(dataKey)) {
            return;
        }
        byte[] part = tag.getByteArray(dataKey);
        tag.remove(dataKey);

        ScreenData screen = manager.getStatus(sender).getScreenData();
        Incoming incoming = INCOMING.computeIfAbsent(screen, s -> new Incoming());
        byte[] whole = assemble(incoming, part,
                tag.getInt(WatutNetworking.NBTDataPlayerScreenCompressedPixelDataPacketIndex),
                tag.getInt(WatutNetworking.NBTDataPlayerScreenCompressedPixelDataPacketCount));
        if (whole == null) {
            return;
        }

        int width = tag.getInt(WatutNetworking.NBTDataPlayerScreenWidth);
        int height = tag.getInt(WatutNetworking.NBTDataPlayerScreenHeight);
        boolean keyframe = !tag.contains(KEY_KEYFRAME) || tag.getBoolean(KEY_KEYFRAME);
        ByteBuffer canvas = FrameCodec.apply(incoming.codec, screen.getDecompressionBuffer(), screen.getWidth(), screen.getHeight(),
                whole, width, height, keyframe, tag.getInt(KEY_SEQ));
        if (canvas == null) {
            WatutLivePreview.Stats.dropped++;
            return;
        }
        WatutLivePreview.Stats.decoded++;
        screen.setDecompressionBuffer(canvas);
        screen.setTexturePixelData(canvas);
        screen.setWidth(width);
        screen.setHeight(height);
        screen.markNeedsNewRenderFromPixelData(true);
        screen.getIsBufferReady().set(true);
    }

    /** @return the full payload once its last chunk arrived in order, otherwise null */
    private static byte[] assemble(Incoming incoming, byte[] part, int index, int count) {
        if (count <= 1) {
            return part;
        }
        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        if (index == 0) {
            //count comes from the network, never let it size the buffer on its own
            incoming.parts = new ByteArrayOutputStream((int) Math.min((long) part.length * count, 1 << 20));
            incoming.startedAt = now;
        } else if (incoming.parts == null || index != incoming.expectedIndex || now - incoming.startedAt > ASSEMBLY_TIMEOUT_TICKS) {
            //out of order, a gap, or too slow: wait for the next frame's first chunk
            incoming.parts = null;
            return null;
        }
        if (incoming.parts.size() + part.length > MAX_ASSEMBLED_BYTES) {
            incoming.parts = null;
            return null;
        }
        incoming.parts.writeBytes(part);
        incoming.expectedIndex = index + 1;
        if (index < count - 1) {
            return null;
        }
        byte[] whole = incoming.parts.toByteArray();
        incoming.parts = null;
        return whole;
    }

    /** Forget a player's partial frame and decoder state. */
    static void forget(ScreenData screen) {
        INCOMING.remove(screen);
    }
}
