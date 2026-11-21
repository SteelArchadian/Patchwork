package dev.chill.patchwork.acceleratedrendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accelerated Rendering frees all of its GPU buffers and compute shaders when any LevelRenderer closes. They are global,
 * so closing a second renderer (a portal mod's far-side view, for example) destroys what the game's own renderer still
 * draws with: GL "buffer name does not exist" / "program not linked" errors, then a native crash writing past a freed
 * buffer. Only the renderer the game draws with (or any renderer during shutdown) may free them.
 */
public final class SecondaryCloseGuard {
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    private static boolean skipping;
    private static boolean logged;

    private SecondaryCloseGuard() {}

    public static void enter(LevelRenderer closing) {
        Minecraft mc = Minecraft.getInstance();
        skipping = mc != null && mc.isRunning() && closing != mc.levelRenderer;
        if (skipping && !logged) {
            logged = true;
            LOG.info("[Patchwork] acceleratedrendering: a secondary LevelRenderer closed; kept Accelerated Rendering's shared buffers and shaders");
        }
    }

    public static void exit() {
        skipping = false;
    }

    /** true while a secondary renderer closes: AR's delete hooks see the feature as not loaded and leave everything alone */
    public static boolean skipping() {
        return skipping;
    }
}
