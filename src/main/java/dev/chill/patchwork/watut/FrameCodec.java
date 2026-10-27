package dev.chill.patchwork.watut;

import dev.chill.patchwork.Patchwork;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encodes and decodes WATUT gui frames.
 *
 * A keyframe is the whole RGBA image deflated. A delta frame is the image XORed against the previously sent frame, then
 * deflated: a mostly static gui (only the cursor, a hover highlight or a progress bar changing) XORs to almost all zero
 * bytes and compresses to a tiny packet, which is what makes leaving the preview live affordable. Frames identical to the
 * last one sent are not sent at all.
 *
 * With {@code -Dpatchwork.verify.watut=true} every delta frame is decoded again on the encoder thread and compared with
 * the captured image, any mismatch is logged.
 */
public final class FrameCodec {
    private FrameCodec() {}

    public static final boolean VERIFY = Boolean.getBoolean("patchwork.verify.watut");

    //biggest frame accepted from the network, WATUT's entire-screen mode at 4k is 3840x2160
    public static final int MAX_FRAME_DIMENSION = 4096;
    public static final int BYTES_PER_PIXEL = 4;

    //safety net, receivers that somehow missed a frame recover within this many sent frames
    public static final int KEYFRAME_INTERVAL = 40;

    public record RawFrame(byte[] pixels, int width, int height) {}

    public record EncodedFrame(byte[] data, int rawSize, int width, int height, boolean keyframe, int seq, boolean unchanged) {}

    /** Receiving side state for one player, see {@link #apply}. */
    public static final class ReceiveState {
        byte[] scratch;
        int lastSeq;
        boolean hasBase;
        byte[] partial;
        int lastIndex;
        long firstPacketTime;
    }

    public static final class Encoder {
        private final AtomicBoolean forceKeyframe = new AtomicBoolean(true);
        private byte[] prevFrame;
        private int seq = 0;
        private int framesSinceKeyframe = 0;
        private long verified = 0;

        /** Next encoded frame will be a full image, used when a new player could be watching or the gui changed. */
        public void requestKeyframe() {
            forceKeyframe.set(true);
        }

        //runs on the encoder thread
        public EncodedFrame encode(RawFrame raw) {
            byte[] frame = raw.pixels();
            boolean keyframe = forceKeyframe.getAndSet(false)
                    || prevFrame == null
                    || prevFrame.length != frame.length
                    || framesSinceKeyframe >= KEYFRAME_INTERVAL;

            byte[] payload;
            if (keyframe) {
                payload = frame;
                framesSinceKeyframe = 0;
            } else {
                payload = new byte[frame.length];
                boolean changed = false;
                for (int i = 0; i < frame.length; i++) {
                    byte diff = (byte) (frame[i] ^ prevFrame[i]);
                    payload[i] = diff;
                    if (diff != 0) changed = true;
                }
                if (!changed) {
                    return new EncodedFrame(null, frame.length, raw.width(), raw.height(), false, seq, true);
                }
                framesSinceKeyframe++;
            }

            byte[] compressed = compress(payload);
            if (VERIFY && !keyframe) {
                verifyDelta(compressed, prevFrame, frame);
            }
            prevFrame = frame;
            seq++;
            return new EncodedFrame(compressed, frame.length, raw.width(), raw.height(), keyframe, seq, false);
        }

        private void verifyDelta(byte[] compressed, byte[] base, byte[] expected) {
            byte[] decoded = new byte[expected.length + 1];
            try {
                int size = inflateInto(compressed, decoded);
                boolean match = size == expected.length;
                for (int i = 0; match && i < expected.length; i++) {
                    match = (byte) (base[i] ^ decoded[i]) == expected[i];
                }
                verified++;
                if (!match) {
                    Patchwork.LOG.warn("[Patchwork/watut verify] delta frame {} did not decode back to the captured image", seq + 1);
                } else if (verified == 1 || verified % 200 == 0) {
                    Patchwork.LOG.info("[Patchwork/watut verify] {} delta frames round-tripped exactly", verified);
                }
            } catch (DataFormatException e) {
                Patchwork.LOG.warn("[Patchwork/watut verify] delta frame {} failed to inflate: {}", seq + 1, e.getMessage());
            }
        }
    }

    public static byte[] compress(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 8));
            byte[] temp = new byte[16384];
            while (!deflater.finished()) {
                int n = deflater.deflate(temp);
                out.write(temp, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * Inflate into a fixed size array. Never grows, and never spins forever on truncated input the way WATUT's
     * {@code while (!inflater.finished())} loop does.
     *
     * @return bytes written
     */
    public static int inflateInto(byte[] input, byte[] output) throws DataFormatException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(input);
            int total = 0;
            while (!inflater.finished()) {
                if (total >= output.length) throw new DataFormatException("frame bigger than expected");
                int n = inflater.inflate(output, total, output.length - total);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new DataFormatException("frame data truncated");
                }
                total += n;
            }
            return total;
        } finally {
            inflater.end();
        }
    }

    /**
     * Decode a received frame into the canvas: keyframes replace it, delta frames are XORed onto it. Everything from the
     * network is validated here; a bad size would otherwise become an out of bounds native copy (a hard JVM crash) when
     * the texture is uploaded.
     *
     * @param canvas the player's current native canvas, may be null
     * @return the canvas now holding the new image (possibly newly allocated, the old one freed), or null if the frame was dropped
     */
    public static ByteBuffer apply(ReceiveState state, ByteBuffer canvas, int canvasWidth, int canvasHeight,
                                   byte[] compressed, int width, int height, boolean keyframe, int seq) {
        if (width <= 0 || height <= 0 || width > MAX_FRAME_DIMENSION || height > MAX_FRAME_DIMENSION) {
            return null;
        }
        int rawSize = width * height * BYTES_PER_PIXEL;

        boolean canvasMatches = canvas != null && canvas.capacity() == rawSize && canvasWidth == width && canvasHeight == height;
        if (!keyframe && (!canvasMatches || !state.hasBase || seq != state.lastSeq + 1)) {
            //missed a frame or only just came into range: nothing to apply this delta to, wait for the next keyframe
            state.hasBase = false;
            return null;
        }

        //one spare byte so a valid frame never fills the array exactly before the inflater reports finished
        if (state.scratch == null || state.scratch.length < rawSize + 1) {
            state.scratch = new byte[rawSize + 1];
        }
        byte[] scratch = state.scratch;
        try {
            if (inflateInto(compressed, scratch) != rawSize) {
                state.hasBase = false;
                return null;
            }
        } catch (DataFormatException e) {
            state.hasBase = false;
            return null;
        }

        if (!canvasMatches) {
            if (canvas != null) {
                MemoryUtil.memFree(canvas);
            }
            canvas = MemoryUtil.memAlloc(rawSize);
        }

        if (keyframe) {
            canvas.clear();
            canvas.put(scratch, 0, rawSize);
            canvas.clear();
        } else {
            ByteBuffer delta = ByteBuffer.wrap(scratch).order(canvas.order());
            int i = 0;
            for (; i + 8 <= rawSize; i += 8) {
                canvas.putLong(i, canvas.getLong(i) ^ delta.getLong(i));
            }
            for (; i < rawSize; i++) {
                canvas.put(i, (byte) (canvas.get(i) ^ scratch[i]));
            }
        }

        state.lastSeq = seq;
        state.hasBase = true;
        return canvas;
    }
}
