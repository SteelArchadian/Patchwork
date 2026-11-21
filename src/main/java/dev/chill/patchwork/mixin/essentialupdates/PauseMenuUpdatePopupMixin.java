package dev.chill.patchwork.mixin.essentialupdates;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops Essential's update popups on the title and pause menus. When Essential finds a new version it shows an
 * "update available" toast, and after it has updated it shows a "what's new" changelog window. Foundation pins its
 * Essential version (the loader's autoUpdate is off), so both only interrupt. The toast is treated as already seen,
 * unless Essential's servers say this version is too old to connect, and the changelog window is skipped. Updating
 * by hand from Essential's sidebar flag or settings still works.
 */
@Pseudo
@Mixin(targets = "gg.essential.handlers.PauseMenuDisplay", remap = false)
public abstract class PauseMenuUpdatePopupMixin {
    @ModifyExpressionValue(method = "initModals", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Lgg/essential/util/AutoUpdate;getSeenUpdateToast()Z", remap = false))
    private boolean patchwork$skipUpdateToast(boolean seen) {
        return seen || !patchwork$updateRequired();
    }

    @ModifyExpressionValue(method = "initModals", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Lgg/essential/config/EssentialConfig;getUpdateModal()Z", remap = false))
    private boolean patchwork$skipChangelogModal(boolean show) {
        return false;
    }

    /** Essential's own check for a version its servers no longer accept; that update must still be offered. */
    @Unique
    private static boolean patchwork$updateRequired() {
        try {
            Class<?> autoUpdate = Class.forName("gg.essential.util.AutoUpdate");
            Object instance = autoUpdate.getField("INSTANCE").get(null);
            return (Boolean) autoUpdate.getMethod("requiresUpdate").invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
