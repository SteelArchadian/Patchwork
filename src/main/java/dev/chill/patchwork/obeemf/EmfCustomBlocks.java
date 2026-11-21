package dev.chill.patchwork.obeemf;

import com.mojang.logging.LogUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * For the obeemf module: the blocks whose block-entity model Entity Model Features replaced with a custom (resource
 * pack) model. Optimized Block Entities cannot bake those (EMF places their cubes only while it draws them), so they
 * are left to their normal renderer, which EMF draws correctly.
 */
public final class EmfCustomBlocks {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<Block> BLOCKS = ConcurrentHashMap.newKeySet();
    /** Set while OBE bakes one model: whether the layer it baked has EMF custom geometry. */
    public static final ThreadLocal<Boolean> BAKING_CUSTOM = new ThreadLocal<>();

    private EmfCustomBlocks() {}

    public static boolean hasCustomGeometry(ModelPart root) {
        for (ModelPart part : root.getAllParts().toList()) {
            if (part.getClass().getName().equals("traben.entity_model_features.models.parts.EMFModelPartCustom") && !part.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static void add(Block block) {
        if (BLOCKS.add(block)) {
            LOG.info("[Patchwork] obeemf: {} has an EMF custom model; left to its normal renderer instead of Optimized Block Entities' baked one", block);
        }
    }

    public static boolean contains(Block block) {
        return BLOCKS.contains(block);
    }

    /** Resource packs changed: EMF's models may have too, so every block is checked again when OBE bakes it. */
    public static void clear() {
        BLOCKS.clear();
    }
}
