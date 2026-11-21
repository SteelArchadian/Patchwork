package dev.chill.patchwork.acceleratedrendering;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Your own player, drawn in first person (First-person Model shows the body and arms), goes through the normal render
 * path instead of Accelerated Rendering's cached GPU meshes. First-person Model, 3D Skin Layers and EMF change that
 * model's parts every frame, and with a shader pack the cached arm mesh came out stretched or flat and untextured.
 * One model per frame, so the cost is nil; every other entity stays accelerated.
 */
public final class OwnPlayerVanilla {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FEATURE = "com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature";
    private static MethodHandle use;
    private static MethodHandle reset;
    private static boolean resolved;
    private static int pushed;

    private OwnPlayerVanilla() {
    }

    private static boolean resolve() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> feature = Class.forName(FEATURE);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                use = lookup.findStatic(feature, "useVanillaPipeline", MethodType.methodType(void.class));
                reset = lookup.findStatic(feature, "resetPipeline", MethodType.methodType(void.class));
                LOG.info("[Patchwork] acceleratedrendering: own first-person player drawn on the normal path");
            } catch (ReflectiveOperationException | LinkageError e) {
                LOG.warn("[Patchwork] acceleratedrendering: pipeline switch not found, own player left as is ({})", e.toString());
            }
        }
        return use != null;
    }

    /** True for the entity the camera is in while the view is first person. */
    public static boolean isOwnFirstPerson(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return entity != null && entity == mc.getCameraEntity() && !mc.gameRenderer.getMainCamera().isDetached();
    }

    public static void enter(boolean applies) {
        if (!applies || !resolve()) return;
        try {
            use.invokeExact();
            pushed++;
        } catch (Throwable t) {
            LOG.warn("[Patchwork] acceleratedrendering: pipeline switch failed ({})", t.toString());
            use = null;
        }
    }

    public static void exit() {
        if (pushed == 0) return;
        try {
            pushed--;
            reset.invokeExact();
        } catch (Throwable t) {
            LOG.warn("[Patchwork] acceleratedrendering: pipeline reset failed ({})", t.toString());
        }
    }
}
