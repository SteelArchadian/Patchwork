package dev.chill.patchwork.c2meoclfallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * C2ME's OpenCL addon can already fall back to its normal CPU world generation when OpenCL can't start (no OpenCL
 * driver, no device, or a GPU without cl_khr_fp64 such as Intel UHD), but that fallback is off by default, so on such
 * PCs the server thread throws "No OpenCL devices found" and the world never loads. C2ME reads a system property before
 * its config file, so Patchwork sets it before C2ME reads its config (mixin plugin load; mods.toml orders Patchwork
 * before the addon). PCs where OpenCL works are unchanged: the fallback only acts when OpenCL fails to start.
 */
public final class OclFallback {
    public static final String PROPERTY = "c2me.base.config.override.openclAccel.allowIncompatibilityFallback";
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    private static boolean set;

    private OclFallback() {
    }

    /** Called from the mixin plugin's onLoad; a value the player set with -D wins. */
    public static void apply() {
        if (System.getProperty(PROPERTY) != null) {
            LOG.info("[Patchwork] c2meoclfallback: -D{}={} set by the player; left as is", PROPERTY, System.getProperty(PROPERTY));
            return;
        }
        System.setProperty(PROPERTY, "true");
        set = true;
        LOG.info("[Patchwork] c2meoclfallback: C2ME OpenCL falls back to CPU world generation if OpenCL can't start (no device, no fp64)");
    }

    /** Called at mod construction: says plainly whether C2ME took the setting (it must read its config after us). */
    public static void verify(ClassLoader loader) {
        if (!set) return;
        try {
            boolean on = Class.forName("com.ishland.c2me.opts.accel.opencl.common.Config", true, loader)
                    .getField("allowIncompatibilityFallback").getBoolean(null);
            if (on) {
                LOG.info("[Patchwork] c2meoclfallback: C2ME OpenCL CPU fallback is on");
            } else {
                LOG.error("[Patchwork] c2meoclfallback: C2ME read its config before Patchwork; CPU fallback is OFF "
                        + "(a PC without OpenCL fp64 will fail to load worlds). Set openclAccel.allowIncompatibilityFallback = true in c2me.toml.");
            }
        } catch (Throwable t) {
            LOG.warn("[Patchwork] c2meoclfallback: could not check C2ME's OpenCL fallback setting ({})", t.toString());
        }
    }
}
