package dev.chill.patchwork.mixin.afkcinema;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Absolute AFK Cinema 1.3.0 hides the HUD (options.hideGui) and swaps the camera when the cinema starts, and puts both
 * back in restoreCameraAndHud, which runs only once per return: it sets viewRestored and skips when it is already set.
 * Only beginReturn clears viewRestored, so after one animated return any later cinema that ends the instant way (a
 * parked inventory reopened, a screen open, no player or level) skips the restore: the hotbar and HUD stay hidden and
 * the camera stays in third person until F1 / F5. Each new cinema gets its own restore.
 */
@Mixin(targets = "de.delinked.absoluteafkcinema.client.CinemaController", remap = false)
public abstract class CinemaControllerMixin {
    @Shadow
    private boolean viewRestored;

    @Inject(method = "start", at = @At("RETURN"), require = 1)
    private void patchwork$freshRestore(boolean manual, CallbackInfo ci) {
        this.viewRestored = false;
    }
}
