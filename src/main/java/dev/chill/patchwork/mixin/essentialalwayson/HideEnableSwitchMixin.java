package dev.chill.patchwork.mixin.essentialalwayson;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves the General > Essential section out of Essential's settings. It holds only "Enable Essential" (kept on by
 * {@link KeepEssentialOnMixin}) and "Auto update" (kept off by the essentialupdates module), so both switches would do
 * nothing.
 */
@Pseudo
@Mixin(targets = "gg.essential.config.EssentialConfig", remap = false)
public abstract class HideEnableSwitchMixin {
    @Inject(method = "gui_delegate$lambda$0$0$0", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void patchwork$noEnableSwitch(CallbackInfoReturnable<Object> cir) {
        try {
            cir.setReturnValue(Class.forName("kotlin.Unit").getField("INSTANCE").get(null));
        } catch (ReflectiveOperationException e) {
            // Leave Essential's section as it is.
        }
    }
}
