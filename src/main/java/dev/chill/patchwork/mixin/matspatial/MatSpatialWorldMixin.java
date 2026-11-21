package dev.chill.patchwork.mixin.matspatial;

import dev.chill.patchwork.matspatial.SpatialCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Inventory Expanded skips drawing the world while its survival inventory is open (the mat covers the whole screen).
 * Spatial GUI shows that screen on a panel inside the world, and the panel itself is drawn during the world pass, so
 * with the world skipped the inventory never appeared. When Spatial GUI has the screen on its panel, the world is
 * drawn as the game intended. Without Spatial GUI nothing changes.
 */
@Mixin(value = GameRenderer.class, priority = 3000)
public abstract class MatSpatialWorldMixin {
    @Unique private static boolean patchwork$logged;

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private boolean patchwork$keepWorldForPanel(boolean renderWorld) {
        if (renderWorld || !SpatialCapture.renderWorld) return renderWorld;
        if (!SpatialCapture.hooks(Minecraft.getInstance().screen)) return renderWorld;
        if (!patchwork$logged) {
            patchwork$logged = true;
            org.slf4j.LoggerFactory.getLogger("Patchwork")
                    .info("[Patchwork] matspatial: world kept on behind Spatial GUI's inventory panel");
        }
        return true;
    }
}
