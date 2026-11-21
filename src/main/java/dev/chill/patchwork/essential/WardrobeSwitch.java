package dev.chill.patchwork.essential;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * One switch at the top of Essential's Wardrobe for the whole Emotes and Cosmetics section. Off sets Essential's own
 * "Disable cosmetics" and "Disable emotes" settings (saved in essential/config.toml) and skips the cosmetics render
 * layer, so nothing of it runs on this client; on clears both. The Wardrobe stays open either way, so the switch is
 * always one click away. Drawn and clicked through screen events, outside Essential's own UI code.
 */
public final class WardrobeSwitch {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WARDROBE = "gg.essential.gui.wardrobe.Wardrobe";
    private static Object config;
    private static Method getCosmetics, setCosmetics, setEmotes, getEnabled, setEnabled;
    private static volatile boolean off;
    private static Button button;
    private static boolean keepOn;
    private static int ticks;

    private WardrobeSwitch() {}

    public static boolean cosmeticsOff() {
        return off;
    }

    /** Hooks the events when Essential is present; returns false (and does nothing) without it. */
    public static boolean init(boolean wardrobeSwitch, boolean keepOn) {
        try {
            Class<?> c = Class.forName("gg.essential.config.EssentialConfig");
            config = c.getField("INSTANCE").get(null);
            getCosmetics = c.getMethod("getDisableCosmetics");
            setCosmetics = c.getMethod("setDisableCosmetics", boolean.class);
            setEmotes = c.getMethod("setDisableEmotes", boolean.class);
            getEnabled = c.getMethod("getEssentialEnabled");
            setEnabled = c.getMethod("setEssentialEnabled", boolean.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
        WardrobeSwitch.keepOn = keepOn;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        if (wardrobeSwitch) {
            NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, WardrobeSwitch::render);
            NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Pre.class, WardrobeSwitch::click);
        }
        return true;
    }

    private static void tick() {
        try {
            // Essential's settings and its "turn cosmetics back on?" toast can change it too; follow them.
            off = (boolean) getCosmetics.invoke(config);
            // Turned off by an old config, a config edit or another mod: turn it back on, there is no switch for it now.
            if (keepOn && ticks++ % 20 == 0 && !(boolean) getEnabled.invoke(config)) {
                setEnabled.invoke(config, true);
                LOG.info("[Patchwork] essentialalwayson: Essential was switched off, switched it back on");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Essential not started yet: try again next tick.
        }
    }

    /** Turns the whole Emotes and Cosmetics section on or off; false if Essential refused. */
    public static boolean set(boolean on) {
        try {
            setCosmetics.invoke(config, !on);
            setEmotes.invoke(config, !on);
            off = !on;
            LOG.info("[Patchwork] essentialwardrobe: cosmetics and emotes {}", on ? "on" : "off");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[Patchwork] essentialwardrobe: could not switch cosmetics", e);
            return false;
        }
    }

    public static boolean isWardrobe(Screen screen) {
        return screen != null && screen.getClass().getName().equals(WARDROBE);
    }

    private static Button button(Screen screen) {
        Component label = Component.translatable(off ? "patchwork_fixes.wardrobe.off" : "patchwork_fixes.wardrobe.on");
        int width = Math.max(150, Minecraft.getInstance().font.width(label) + 20);
        if (button == null || button.getWidth() != width) {
            button = Button.builder(label, b -> set(off)).width(width).build();
        }
        button.setMessage(label);
        button.setPosition((screen.width - width) / 2, 2);
        return button;
    }

    private static void render(ScreenEvent.Render.Post event) {
        if (isWardrobe(event.getScreen())) {
            button(event.getScreen()).render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
    }

    private static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && isWardrobe(event.getScreen())) {
            Button b = button(event.getScreen());
            if (b.isMouseOver(event.getMouseX(), event.getMouseY())) {
                b.playDownSound(Minecraft.getInstance().getSoundManager());
                set(off);
                event.setCanceled(true);
            }
        }
    }
}
