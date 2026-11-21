package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.ebalttab=true (test copies only). Sets Effortless
 * Building to Wall, opens its build wheel with Cube "under the mouse" and presses Tab on it: the mode must stay Wall.
 * Then opens it again with Cube under the mouse and closes it the normal way (Alt released): the mode must become
 * Cube, so normal picking still works. Calls the wheel's own methods; no real keys or mouse.
 */
public final class EbAltTabTest {
    public static final boolean ON = Boolean.getBoolean("patchwork.test.ebalttab");
    private static final int AT = Integer.getInteger("patchwork.test.ebalttab.at", 100);
    private static final String EB = "neoforge.nl.requios.effortlessbuilding.";
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;

    private EbAltTabTest() {}

    private static Object modes() throws ReflectiveOperationException {
        return Class.forName(EB + "buildmode.BuildModes").getField("CLIENT").get(null);
    }

    private static Object mode(String name) throws ReflectiveOperationException {
        Class<?> e = Class.forName(EB + "buildmode.BuildModeEnum");
        return e.getField(name).get(null);
    }

    private static Object current() throws ReflectiveOperationException {
        Object m = modes();
        return m.getClass().getMethod("getBuildMode").invoke(m);
    }

    private static Screen wheel(String hovered) throws ReflectiveOperationException {
        Class<?> c = Class.forName(EB + "screen.RadialMenu");
        Screen s = (Screen) c.getField("instance").get(null);
        Minecraft.getInstance().setScreen(s);
        c.getField("performedActionUsingMouse").setBoolean(s, false);
        c.getField("switchTo").set(s, mode(hovered));
        return s;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ticks > AT + 40) {
            return;
        }
        int t = ++ticks - AT;
        try {
            if (t == 0) {
                Object m = modes();
                m.getClass().getMethod("setBuildMode", Class.forName(EB + "buildmode.BuildModeEnum")).invoke(m, mode("WALL"));
                Screen s = wheel("CUBE");
                s.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_ALT);
                String after = String.valueOf(current());
                LOG.info("[Patchwork/test] ebalttab: Tab on the wheel: mode {} (want WALL), wheel {}: {}", after,
                        mc.screen == s ? "still open" : "closed", "WALL".equals(after) && mc.screen != s ? "PASS" : "FAIL");
            } else if (t == 20) {
                Screen s = wheel("CUBE");
                s.onClose();
                String after = String.valueOf(current());
                LOG.info("[Patchwork/test] ebalttab: normal release: mode {} (want CUBE): {}", after,
                        "CUBE".equals(after) ? "PASS" : "FAIL");
                Object m = modes();
                m.getClass().getMethod("setBuildMode", Class.forName(EB + "buildmode.BuildModeEnum")).invoke(m, mode("DISABLED"));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[Patchwork/test] ebalttab: step {} failed", t, e);
        }
    }
}
