package dev.chill.patchwork.devtest;

import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.fullscreen=true (test copies only; it covers the
 * monitor, so run it only while the user is away). Once in the world it presses F11 four times, 3 s apart, the way
 * the keyboard handler does, and logs the window's real state 2 s after each press: the game's fullscreen flags,
 * the window's decoration, monitor, position and size, and the monitor size. It ends windowed.
 */
public final class FullscreenTest {
    public static final boolean ON = Boolean.getBoolean("patchwork.test.fullscreen");
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;

    private FullscreenTest() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ticks++;
        int t = ticks - 200;
        if (t < 0 || t > 4 * 60 + 40) {
            return;
        }
        if (t % 60 == 0 && t < 4 * 60) {
            Window window = mc.getWindow();
            window.toggleFullScreen();
            mc.options.fullscreen().set(window.isFullscreen());
            LOG.info("[Patchwork/test] fullscreen: pressed F11 #{}", t / 60 + 1);
        }
        if (t % 60 == 40 || t == 0) {
            log(mc, t == 0 ? "before" : "after F11 #" + (t / 60 + 1));
        }
    }

    private static void log(Minecraft mc, String when) {
        Window window = mc.getWindow();
        long handle = window.getWindow();
        int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
        GLFW.glfwGetWindowPos(handle, x, y);
        GLFW.glfwGetWindowSize(handle, w, h);
        long monitor = GLFW.glfwGetPrimaryMonitor();
        var mode = monitor == 0 ? null : GLFW.glfwGetVideoMode(monitor);
        LOG.info("[Patchwork/test] fullscreen: {}: isFullscreen={} option={} decorated={} onMonitor={} pos={},{} size={}x{} game={}x{} monitor={}",
                when, window.isFullscreen(), mc.options.fullscreen().get(),
                GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED), GLFW.glfwGetWindowMonitor(handle) != 0,
                x[0], y[0], w[0], h[0], window.getWidth(), window.getHeight(),
                mode == null ? "?" : mode.width() + "x" + mode.height());
    }
}
