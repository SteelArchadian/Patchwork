package dev.chill.patchwork.watut;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;

/**
 * Reads the scaled down gui framebuffer back to the cpu without stalling the render thread.
 *
 * A plain glReadPixels makes the cpu wait for the gpu to finish everything queued so far, which shows up as a hitch every capture.
 * Instead we read into a pixel buffer object, fence it, and collect the pixels on a later frame once the gpu says its done.
 */
public class AsyncFrameReader {

    private int pbo = 0;
    private int pboSize = 0;
    private long fence = 0;
    private int width;
    private int height;

    public boolean isPending() {
        return fence != 0;
    }

    /**
     * Queue a read of the currently bound framebuffer, render thread only
     */
    public void start(int width, int height) {
        discard();
        int size = width * height * FrameCodec.BYTES_PER_PIXEL;
        if (pbo == 0) {
            pbo = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
        if (pboSize != size) {
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, size, GL15.GL_STREAM_READ);
            pboSize = size;
        }
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        //must unbind, vanilla screenshots and texture downloads read into client memory and would break with a pack buffer bound
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        this.width = width;
        this.height = height;
    }

    /**
     * @return the frame once the gpu finished copying it, null while still in flight or if nothing was queued, render thread only
     */
    public FrameCodec.RawFrame poll() {
        if (fence == 0) return null;
        int status = GL32.glClientWaitSync(fence, 0, 0L);
        if (status == GL32.GL_TIMEOUT_EXPIRED) return null;
        GL32.glDeleteSync(fence);
        fence = 0;
        if (status == GL32.GL_WAIT_FAILED) return null;

        FrameCodec.RawFrame frame = null;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
        ByteBuffer mapped = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0, pboSize, GL30.GL_MAP_READ_BIT);
        if (mapped != null) {
            byte[] pixels = new byte[pboSize];
            mapped.get(pixels);
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            frame = new FrameCodec.RawFrame(pixels, width, height);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        return frame;
    }

    /**
     * Drop a pending read, eg the gui closed before it finished
     */
    public void discard() {
        if (fence != 0) {
            GL32.glDeleteSync(fence);
            fence = 0;
        }
    }
}
