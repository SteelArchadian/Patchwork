package dev.chill.patchwork.mixin.woverregistrypath;

import dev.chill.patchwork.woverregistrypath.WoverRegistryPath;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** See WoverRegistryPath. */
// priority < 200 (WorldWeaver uses 200): run before WorldWeaver's own cancellable RETURN hook, which returns early for its registries
@Mixin(value = Registries.class, priority = 100)
public abstract class ElementsDirPathMixin {
    @Inject(method = "elementsDirPath", at = @At("RETURN"), cancellable = true)
    private static void patchwork$woverPath(ResourceKey<? extends Registry<?>> key, CallbackInfoReturnable<String> cir) {
        String fixed = WoverRegistryPath.fix(key, cir.getReturnValue());
        if (fixed != null) cir.setReturnValue(fixed);
    }
}
