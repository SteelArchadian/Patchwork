package dev.chill.patchwork.mixin.fabricattributes;

import dev.chill.patchwork.fabricattributes.EarlyAttributes;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the held Fabric registrations over just before NeoForge collects mob attributes; see {@link EarlyAttributes}. */
@Mixin(value = CommonHooks.class, remap = false)
public abstract class ReleaseMixin {
    @Inject(method = "modifyAttributes", at = @At("HEAD"), remap = false)
    private static void patchwork$releaseHeld(CallbackInfo ci) {
        EarlyAttributes.release();
    }
}
