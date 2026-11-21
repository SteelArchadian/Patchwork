package dev.chill.patchwork.mixin.matspatial;

import dev.chill.patchwork.matspatial.SpatialCapture;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Runs before other mods: remembers whether the game meant to draw the world this frame (see MatSpatialWorldMixin). */
@Mixin(value = GameRenderer.class, priority = 100)
public abstract class MatSpatialWorldSaveMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private boolean patchwork$saveRenderWorld(boolean renderWorld) {
        SpatialCapture.renderWorld = renderWorld;
        return renderWorld;
    }
}
