package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import dev.chill.patchwork.essential.WardrobeSwitch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.essentialscreens=true (test copies only). Once in
 * the world it opens Essential's Wardrobe and screenshots it with cosmetics on, switches them off the way the
 * Wardrobe switch does and screenshots again, switches them back on, then opens Essential's settings on General,
 * Cosmetics and Privacy & Data and screenshots each. It also tries to turn Essential off and logs whether it stayed on.
 */
public final class EssentialScreenTest {
    public static final boolean ON = Boolean.getBoolean("patchwork.test.essentialscreens");
    private static final int AT = Integer.getInteger("patchwork.test.essentialscreens.at", 200);
    private static final int STEP = 60;
    /** Step to start from, e.g. 9 for only the Privacy & Data page and the "turn Essential off" check. */
    private static final int FROM = Integer.getInteger("patchwork.test.essentialscreens.from", 0);
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;

    private EssentialScreenTest() {}

    private static Object config() throws ReflectiveOperationException {
        return Class.forName("gg.essential.config.EssentialConfig").getField("INSTANCE").get(null);
    }

    private static Object call(String method) throws ReflectiveOperationException {
        Object c = config();
        return c.getClass().getMethod(method).invoke(c);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        LOG.info("[Patchwork/test] essentialscreens {}: screen {}", name, s == null ? "none" : s.getClass().getName());
        Screenshot.grab(mc.gameDirectory, "essential-" + name + ".png", mc.getMainRenderTarget(),
                m -> LOG.info("[Patchwork/test] essentialscreens {}: {}", name, m.getString()));
    }

    private static void settings(String category) throws ReflectiveOperationException {
        Class<?> gui = Class.forName("gg.essential.gui.vigilancev2.VigilanceV2SettingsGui");
        Class<?> state = Class.forName("gg.essential.gui.elementa.state.v2.State");
        Object props = call("getGui");
        Minecraft.getInstance().setScreen((Screen) gui.getConstructor(state, String.class).newInstance(props, category));
    }

    private static void states(String when) throws ReflectiveOperationException {
        LOG.info("[Patchwork/test] essentialscreens {}: enabled={} disableCosmetics={} disableEmotes={}", when,
                call("getEssentialEnabled"), call("getDisableCosmetics"), call("getDisableEmotes"));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ticks > AT + STEP * (12 - FROM)) {
            return;
        }
        int t = ++ticks - AT;
        if (t < 0 || t % STEP != 0) {
            return;
        }
        try {
            switch (t / STEP + FROM) {
                case 0 -> {
                    states("start");
                    WardrobeSwitch.set(true);
                    mc.setScreen((Screen) Class.forName("gg.essential.gui.wardrobe.Wardrobe").getConstructor().newInstance());
                }
                case 2 -> shot("wardrobe-on");
                case 3 -> {
                    WardrobeSwitch.set(false);
                    states("switched off");
                }
                case 4 -> shot("wardrobe-off");
                case 5 -> {
                    WardrobeSwitch.set(true);
                    states("switched on");
                    settings("General");
                }
                case 6 -> shot("settings-general");
                case 7 -> settings("Cosmetics");
                case 8 -> shot("settings-cosmetics");
                case 9 -> settings("Privacy & Data");
                case 10 -> shot("settings-privacy");
                case 11 -> {
                    Object c = config();
                    c.getClass().getMethod("setEssentialEnabled", boolean.class).invoke(c, false);
                    states("after trying to turn Essential off");
                    mc.setScreen(null);
                }
                default -> { }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[Patchwork/test] essentialscreens step {} failed", t / STEP, e);
        }
    }
}
