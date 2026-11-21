package dev.chill.patchwork.mixin.essentialalwayson;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Essential's "Enable Essential" switch shrinks Essential to a minimal menu with no way back in game: the switch only
 * comes back by editing essential/config.toml. In a pack everyone plays with Essential, so it stays on: whatever turns
 * it off (the switch, an old config) is applied as "on" instead.
 */
@Pseudo
@Mixin(targets = "gg.essential.config.McEssentialConfig", remap = false)
public abstract class KeepEssentialOnMixin {
    @ModifyVariable(method = "toggleEssential", at = @At("HEAD"), argsOnly = true, require = 1, remap = false)
    private boolean patchwork$alwaysOn(boolean enabling) {
        return true;
    }
}
