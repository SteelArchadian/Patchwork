package dev.chill.patchwork.iris;

import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL44;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

/** Fills a freshly allocated shader-pack image with zeros (glClearTexImage, GL 4.4 / ARB_clear_texture). */
public final class ImageZeroer {
    private static final Logger LOG = LogUtils.getLogger();
    private static Boolean supported;
    private static int cleared;

    private ImageZeroer() {}

    public static void zero(int textureId, int pixelFormat, int pixelType, String name) {
        if (supported == null) {
            supported = GL.getCapabilities().glClearTexImage != 0L;
            if (!supported) {
                LOG.warn("[Patchwork] iris: glClearTexImage not available on this GPU driver, shader-pack images stay as Iris leaves them");
            }
        }
        if (!supported) {
            return;
        }
        GL44.glClearTexImage(textureId, 0, pixelFormat, pixelType, (ByteBuffer) null);
        if (cleared++ < 16) {
            LOG.debug("[Patchwork] iris: zeroed shader-pack image {}", name);
        }
    }
}
