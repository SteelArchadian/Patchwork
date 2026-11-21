package dev.chill.patchwork.mixin.colorwheelfallback;

import dev.djefrey.colorwheel.ClrwlBackend;
import dev.djefrey.colorwheel.ClrwlConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * With a shader pack that has no Colorwheel version (most packs), Colorwheel hands Flywheel's models (Create shafts,
 * cogs, contraptions) back to a path the pack does not know: they come out tinted wrong and cast no shadows.
 * Colorwheel's fallback mode instead draws them through the pack's own block shaders, which looks right (A/B with
 * Derivative 26.1, 2026-11-08: cogs keep their wood colour and get shadows). Colorwheel ships it off and asks the
 * player to turn it on by command; this module uses it whenever the pack has no Colorwheel version. Packs that do
 * have one still use their own Colorwheel shaders, since this check only runs for packs without them.
 */
@Mixin(value = ClrwlBackend.class, remap = false)
public abstract class AutoFallbackMixin {
    @Redirect(method = "isUsingCompatibleShaderPack", require = 1,
            at = @At(value = "INVOKE", target = "Ldev/djefrey/colorwheel/ClrwlConfig;isFallbackModeEnabled()Z"))
    private static boolean patchwork$fallbackForUnsupportedPacks(ClrwlConfig config) {
        return true;
    }
}
