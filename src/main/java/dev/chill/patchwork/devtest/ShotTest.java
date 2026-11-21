package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.shots=&lt;label&gt; (test copies only): hides the
 * HUD once the player is in the world and saves a screenshot 15 s and 25 s in, logging each file's name.
 */
public final class ShotTest {
    public static final String LABEL = System.getProperty("patchwork.test.shots");
    /** -Dpatchwork.test.shots.anyscreen=true: also shoot while a screen is open (to check that screen). */
    private static final boolean ANY_SCREEN = Boolean.getBoolean("patchwork.test.shots.anyscreen");
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;

    private ShotTest() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.screen != null && !ANY_SCREEN)) {
            return;
        }
        ticks++;
        if (ticks == 20) {
            mc.options.hideGui = true;
        }
        if (ticks == 300 || ticks == 500) {
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                    message -> LOG.info("[Patchwork/test] shots {}: {}", LABEL, message.getString()));
        }
    }
}
