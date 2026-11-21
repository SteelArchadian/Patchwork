package dev.chill.patchwork.mixin.spatialessential;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.chill.patchwork.spatialessential.EssentialScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spatial GUI asks "is Essential calling?" on every Window.getGuiScaledWidth/Height/getGuiScale read during its render
 * pass, and answers with a StackWalker walk each time. Essential's menus read the window size many times per frame, so
 * the walks cost real render-thread time. When the read comes from Essential's UResolution (marked by
 * UResolutionScopeMixin) the answer is known without a walk: the frame right under Window is UResolution, which is
 * exactly the frame Spatial GUI's walk would find. Every other caller still gets Spatial GUI's own walk.
 */
@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.compat.EssentialCompat", remap = false)
public abstract class SpatialCallerMixin {
    @Unique private static final Logger patchwork$LOG = LoggerFactory.getLogger("Patchwork");
    @Unique private static boolean patchwork$logged;

    @Inject(method = "isEssentialCaller", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void patchwork$knownCaller(CallbackInfoReturnable<Boolean> cir) {
        if (EssentialScope.depth > 0 && RenderSystem.isOnRenderThread()) {
            if (!patchwork$logged) {
                patchwork$logged = true;
                patchwork$LOG.info("[Patchwork] spatialessential: Essential window-size read answered without a stack walk");
            }
            cir.setReturnValue(true);
        }
    }
}
