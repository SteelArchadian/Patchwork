package dev.chill.patchwork.accessoriesmodelleak;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Accessories wraps EntityModelSet.bakeLayer and pushes every baked root ModelPart onto a queue in the EntityModelSet,
 * so the next Model constructor can take it. Only non-hierarchical Model constructors take one, and nothing else ever
 * empties the queue, so every root baked for a hierarchical model, or baked by a mod while playing, stays in memory for
 * good (Odyssey heap 11-17: 252 MB of ModelParts, growing with playtime). A bake and the Model constructor that takes
 * its root always run in the same call, so at the end of each client tick the queue holds only roots no model will take;
 * they are dropped there. Render thread only.
 */
public final class ModelQueueClear {
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    private static Method clear;
    private static boolean failed;
    private static boolean logged;

    private ModelQueueClear() {
    }

    public static void tick() {
        if (failed) return;
        Object set = Minecraft.getInstance().getEntityModels();
        if (set == null) return;
        try {
            if (clear == null) clear = set.getClass().getMethod("accessories$clearQueue");
            clear.invoke(set);
            if (!logged) {
                logged = true;
                LOG.info("[Patchwork] accessoriesmodelleak: unused Accessories model roots are dropped each tick");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            LOG.warn("[Patchwork] accessoriesmodelleak: Accessories model queue not found, module off ({})", e.toString());
        }
    }
}
