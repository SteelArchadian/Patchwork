package dev.chill.patchwork.mixin.acceleratedrendering;

import dev.chill.patchwork.acceleratedrendering.ShaderTranslucentGuard;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the span of world rendering (including the shadow pass), so the GUI keeps Accelerated Rendering's batching. */
@Mixin(LevelRenderer.class)
public class LevelRendererScopeMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void patchwork$enter(CallbackInfo ci) {
        ShaderTranslucentGuard.enterLevel();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void patchwork$exit(CallbackInfo ci) {
        ShaderTranslucentGuard.exitLevel();
    }
}
