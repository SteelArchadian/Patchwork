package dev.chill.patchwork.mixin.colorwheelalert;

import dev.djefrey.colorwheel.ClrwlBackend;
import dev.djefrey.colorwheel.ClrwlConfig;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Colorwheel posts a red chat alert whenever the shader pack in use has no Colorwheel support, every time shaders
 * load. Most packs have none, so it is noise. With this module on the alert is only posted when Colorwheel knows a
 * ready-made Colorwheel version of the pack the player could pick instead (the useful case); Colorwheel's own
 * alertIncompatiblePack setting still turns even that one off.
 */
@Mixin(value = ClrwlBackend.class, remap = false)
public abstract class IncompatibleAlertMixin {
    @Redirect(method = "isUsingCompatibleShaderPack", require = 1,
            at = @At(value = "INVOKE", target = "Ldev/djefrey/colorwheel/ClrwlConfig;shouldAlertIncompatiblePack()Z"))
    private static boolean patchwork$onlyWithPatchedPack(ClrwlConfig config) {
        return config.shouldAlertIncompatiblePack() && ClrwlBackend.findPatchedShaderpack(Iris.getCurrentPackName()).isPresent();
    }
}
