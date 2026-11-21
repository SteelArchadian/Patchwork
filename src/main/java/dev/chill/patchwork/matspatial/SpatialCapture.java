package dev.chill.patchwork.matspatial;

import java.lang.reflect.Field;

/** Shared state for the matspatial module: Spatial GUI's "capturing the screen" flag, read by reflection. */
public final class SpatialCapture {
    private static Field flag;
    private static boolean looked;
    /** True while Patchwork has lowered Spatial GUI's flag for Inventory Expanded's mat render. */
    public static boolean lowered;

    private SpatialCapture() {}

    public static Field flag() {
        if (!looked) {
            looked = true;
            try {
                Field f = Class.forName("org.tastytrash.spatialGUI.render.SpatialGUIRenderer").getField("isExtractingScreen");
                if (f.getType() == boolean.class) flag = f;
            } catch (Throwable ignored) {
                // no capturing GUI mod
            }
        }
        return flag;
    }

    private static java.lang.reflect.Method hooked;
    private static boolean lookedHook;
    /** Value of GameRenderer.render's renderWorld before other mods changed it this frame. */
    public static boolean renderWorld;

    /** Spatial GUI shows this screen on its in-world panel (needs the world drawn behind it). */
    public static boolean hooks(Object screen) {
        if (screen == null) return false;
        if (!lookedHook) {
            lookedHook = true;
            try {
                hooked = Class.forName("org.tastytrash.spatialGUI.api.SpatialGUIAPI").getMethod("getCurrentScreen");
            } catch (Throwable ignored) {
                // no Spatial GUI
            }
        }
        if (hooked == null) return false;
        try {
            return hooked.invoke(null) == screen;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Spatial GUI is capturing the screen into its in-world panel right now. */
    public static boolean active() {
        if (lowered) return true;
        Field f = flag();
        if (f == null) return false;
        try {
            return f.getBoolean(null);
        } catch (Throwable e) {
            return false;
        }
    }
}
