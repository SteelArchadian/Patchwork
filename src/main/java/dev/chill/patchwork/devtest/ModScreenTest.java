package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.modscreen=&lt;mod id&gt; (test copies only). In
 * the world it opens that mod's settings the way the mod list's Config button does, logs its widgets, presses the
 * Patchwork button there and logs the switches it shows; then it opens the first screen class whose name is given
 * by -Dpatchwork.test.modscreen.direct (the way the mod opens its settings itself) and checks for the button again.
 */
public final class ModScreenTest {
    public static final String MOD = System.getProperty("patchwork.test.modscreen");
    private static final String DIRECT = System.getProperty("patchwork.test.modscreen.direct");
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;
    private static String clicked;

    private ModScreenTest() {}

    private static Button patchworkButton(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof Button b && b.getMessage().getString().equals("Patchwork")) {
                return b;
            }
        }
        return null;
    }

    private static void logWidgets(String what, Screen screen) {
        LOG.info("[Patchwork/test] modscreen: {} is {} ({}x{})", what, screen == null ? "none" : screen.getClass().getName(),
                screen == null ? 0 : screen.width, screen == null ? 0 : screen.height);
        if (screen == null) {
            return;
        }
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w) {
                LOG.info("[Patchwork/test] modscreen:   {} '{}' at {},{} {}x{}", w.getClass().getSimpleName(),
                        w.getMessage().getString(), w.getX(), w.getY(), w.getWidth(), w.getHeight());
            }
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        try {
            switch (++ticks) {
                case 200 -> {
                    var info = ModList.get().getModContainerById(MOD).orElseThrow().getModInfo();
                    var factory = IConfigScreenFactory.getForMod(info);
                    LOG.info("[Patchwork/test] modscreen: config factory present={}", factory.isPresent());
                    factory.ifPresent(f -> mc.setScreen(f.createScreen(ModList.get().getModContainerById(MOD).orElseThrow(), null)));
                }
                case 240 -> {
                    logWidgets("mod list settings", mc.screen);
                    Button b = mc.screen == null ? null : patchworkButton(mc.screen);
                    LOG.info("[Patchwork/test] modscreen: {}", b != null ? "PASS button in the mod's settings" : "FAIL no Patchwork button");
                    if (b != null) {
                        b.onPress();
                    }
                }
                case 280 -> logWidgets("after pressing Patchwork", mc.screen);
                case 300 -> {
                    mc.screen.onClose();
                    logWidgets("after Done", mc.screen);
                    mc.setScreen(null);
                }
                case 340 -> {
                    if (DIRECT != null) {
                        Screen screen = (Screen) Class.forName(DIRECT).getConstructor().newInstance();
                        mc.setScreen(screen);
                    }
                }
                case 380 -> {
                    if (DIRECT != null) {
                        logWidgets("opened directly", mc.screen);
                        LOG.info("[Patchwork/test] modscreen: direct {}", mc.screen != null && patchworkButton(mc.screen) != null
                                ? "PASS button there too" : "FAIL no button");
                        mc.setScreen(null);
                    }
                }
                case 420 -> mc.setScreen(new net.neoforged.neoforge.client.gui.ModListScreen(null));
                case 440 -> {
                    logWidgets("mod list", mc.screen);
                    Button recent = null;
                    for (var child : mc.screen.children()) {
                        if (child instanceof Button b && b.getMessage().getString().equals("Recent")) {
                            recent = b;
                        }
                    }
                    LOG.info("[Patchwork/test] modlist: {}", recent != null ? "PASS Recent button" : "FAIL no Recent button");
                    // Click the last mod in the list the way the mouse does; Recent must then put it first.
                    var listField = mc.screen.getClass().getDeclaredField("modList");
                    listField.setAccessible(true);
                    var entries = ((net.neoforged.neoforge.client.gui.widget.ModListWidget) listField.get(mc.screen)).children();
                    var last = entries.get(entries.size() - 1);
                    clicked = last.getInfo().getModId();
                    last.mouseClicked(0, 0, 0);
                    LOG.info("[Patchwork/test] modlist: clicked {}", clicked);
                    if (recent != null) {
                        recent.onPress();
                    }
                }
                case 480 -> {
                    var field = mc.screen.getClass().getDeclaredField("mods");
                    field.setAccessible(true);
                    var mods = (java.util.List<?>) field.get(mc.screen);
                    StringBuilder top = new StringBuilder();
                    for (int i = 0; i < Math.min(8, mods.size()); i++) {
                        top.append(((net.neoforged.fml.ModContainer) mods.get(i)).getModId()).append(' ');
                    }
                    LOG.info("[Patchwork/test] modlist: Recent order starts {}", top);
                    LOG.info("[Patchwork/test] modlist: {}", top.toString().startsWith(clicked + " ")
                            ? "PASS clicked mod is first under Recent" : "FAIL clicked " + clicked + " is not first");
                    logWidgets("mod list after Recent", mc.screen);
                    mc.setScreen(null);
                }
                default -> {}
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.info("[Patchwork/test] modscreen: FAIL {}", e.toString());
            ticks = 1000;
        }
    }
}
