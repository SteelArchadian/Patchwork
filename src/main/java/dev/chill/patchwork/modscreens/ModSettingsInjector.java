package dev.chill.patchwork.modscreens;

import com.mojang.logging.LogUtils;
import dev.chill.patchwork.Module;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * Puts Patchwork's switches for a mod inside that mod's own settings, so they are found where the mod's other options
 * are, not only under Patchwork. Works the same for every mod a module patches, whatever its settings screen is built
 * with: a small "Patchwork" button is added to the mod's settings screen and opens {@link PatchworkModScreen} with the
 * switches for that mod. A mod with no settings screen at all gets one (the Config button in the mod list then opens
 * Patchwork's switches for it).
 * A screen counts as a mod's settings screen when the mod list's Config button made it, or when it is a screen class
 * from that mod's jar named like a settings screen (Config, Settings, Options), which catches settings a mod opens
 * from its own menus.
 */
public final class ModSettingsInjector {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Pattern SETTINGS_NAME = Pattern.compile("(?i).*(config|setting|option).*");
    private static final int BUTTON_W = 64, BUTTON_H = 16;
    /** Mod id → its modules, for mods that are installed and have at least one module. */
    private static final Map<String, List<Module>> MODULES = new LinkedHashMap<>();
    /** JPMS module name of each such mod's jar → mod id. */
    private static final Map<String, String> JAR_MODULES = new HashMap<>();
    /** Screens the mod list's Config button made, and their classes (opened again from the mod's own menus). */
    private static final Map<Screen, String> MADE = new WeakHashMap<>();
    private static final Map<Class<?>, String> MADE_CLASSES = new HashMap<>();

    private ModSettingsInjector() {}

    public static void init(IEventBus modBus) {
        // Wrapped when the first screen opens (the title screen), after loading has finished: by then every mod has
        // registered its own settings screen, and Configured has made its generated ones for mods without any.
        // Wrapping earlier would make Configured see Patchwork's screen and skip that mod.
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, ModSettingsInjector::onInit);
    }

    public static List<Module> modulesFor(String modId) {
        return MODULES.getOrDefault(modId, List.of());
    }

    private static void wrapFactories() {
        for (Module module : Module.values()) {
            String modId = module.targetModId;
            if (modId == null || modId.equals("minecraft") || modId.equals("neoforge") || Module.modVersion(modId) == null) {
                continue;
            }
            MODULES.computeIfAbsent(modId, k -> new ArrayList<>()).add(module);
        }
        for (String modId : MODULES.keySet()) {
            try {
                ModContainer container = ModList.get().getModContainerById(modId).orElse(null);
                if (container == null) {
                    continue;
                }
                IModInfo info = container.getModInfo();
                JAR_MODULES.put(info.getOwningFile().moduleName(), modId);
                IConfigScreenFactory original = container.getCustomExtension(IConfigScreenFactory.class).orElse(null);
                container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> {
                    Screen screen = original == null ? null : original.createScreen(mod, parent);
                    if (screen == null) {
                        return new PatchworkModScreen(parent, modId, info.getDisplayName());
                    }
                    MADE.put(screen, modId);
                    MADE_CLASSES.put(screen.getClass(), modId);
                    return screen;
                });
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("[Patchwork] could not add Patchwork's switches to {}'s settings: {}", modId, e.toString());
            }
        }
    }

    private static String settingsModOf(Screen screen) {
        String modId = MADE.get(screen);
        if (modId != null) {
            return modId;
        }
        Class<?> type = screen.getClass();
        modId = MADE_CLASSES.get(type);
        if (modId != null) {
            return modId;
        }
        modId = JAR_MODULES.get(type.getModule().getName());
        return modId != null && SETTINGS_NAME.matcher(type.getSimpleName()).matches() ? modId : null;
    }

    private static boolean wrapped;

    private static void onInit(ScreenEvent.Init.Post event) {
        if (!wrapped) {
            wrapped = true;
            wrapFactories();
        }
        Screen screen = event.getScreen();
        if (screen instanceof PatchworkModScreen || MODULES.isEmpty()) {
            return;
        }
        String modId;
        try {
            modId = settingsModOf(screen);
        } catch (RuntimeException e) {
            return;
        }
        if (modId == null) {
            return;
        }
        String name = ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
        int[] spot = freeCorner(screen.width, screen.height, event.getListenersList());
        event.addListener(Button.builder(Component.translatable("patchwork_fixes.modscreen.button"),
                        b -> screen.getMinecraft().setScreen(new PatchworkModScreen(screen, modId, name)))
                .bounds(spot[0], spot[1], BUTTON_W, BUTTON_H)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("patchwork_fixes.modscreen.button.tooltip", name)))
                .build());
    }

    /** The first screen corner the mod's own widgets leave free: top right, top left, bottom right, bottom left. */
    private static int[] freeCorner(int width, int height, List<GuiEventListener> widgets) {
        int[][] corners = {{width - BUTTON_W - 4, 4}, {4, 4}, {width - BUTTON_W - 4, height - BUTTON_H - 4}, {4, height - BUTTON_H - 4}};
        for (int[] corner : corners) {
            ScreenRectangle spot = new ScreenRectangle(corner[0], corner[1], BUTTON_W, BUTTON_H);
            boolean free = true;
            for (GuiEventListener widget : widgets) {
                if (widget instanceof AbstractWidget w && w.visible && w.getRectangle().intersection(spot) != null) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return corner;
            }
        }
        return corners[0];
    }
}
