package dev.chill.patchwork.mixin.irispathchar;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Iris lets shader packs use capital letters in resource paths with a cancellable inject at the head of
 * ResourceLocation.validPathChar. That method runs once per character of every resource path, so each call made a
 * CallbackInfoReturnable (about 25 MB/s on Test Dummy). Applied after Iris (priority 1100), this replaces the method
 * with the same rule, vanilla's characters plus A-Z, and no callback. Iris's other head inject, on isValidPath (the
 * path "DUMMY" is never valid), made one more callback per path checked; Patchwork's plugin folds that rule in too
 * (see PathCharRewrite).
 */
@Mixin(value = ResourceLocation.class, priority = 1100)
public abstract class ValidPathCharMixin {
    /**
     * @author Patchwork
     * @reason vanilla's check plus Iris's capital letters, without a callback object per character
     */
    @Overwrite
    public static boolean validPathChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.'
                || c >= 'A' && c <= 'Z';
    }
}
