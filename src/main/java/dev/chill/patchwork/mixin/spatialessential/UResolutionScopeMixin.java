package dev.chill.patchwork.mixin.spatialessential;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.chill.patchwork.spatialessential.EssentialScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Marks Essential's own Window size reads (UResolution.getScaledWidth / getScaledHeight / getScaleFactor) so Spatial
 * GUI can tell at once that Essential is the caller; see SpatialCallerMixin.
 */
@Pseudo
@Mixin(targets = "gg.essential.universal.UResolution", remap = false)
public abstract class UResolutionScopeMixin {
    @WrapOperation(method = "getScaledWidth",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"),
            remap = false, require = 0)
    private static int patchwork$width(Window window, Operation<Integer> original) {
        if (!RenderSystem.isOnRenderThread()) return original.call(window);
        EssentialScope.depth++;
        try {
            return original.call(window);
        } finally {
            EssentialScope.depth--;
        }
    }

    @WrapOperation(method = "getScaledHeight",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"),
            remap = false, require = 0)
    private static int patchwork$height(Window window, Operation<Integer> original) {
        if (!RenderSystem.isOnRenderThread()) return original.call(window);
        EssentialScope.depth++;
        try {
            return original.call(window);
        } finally {
            EssentialScope.depth--;
        }
    }

    @WrapOperation(method = "getScaleFactor",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScale()D"),
            remap = false, require = 0)
    private static double patchwork$scale(Window window, Operation<Double> original) {
        if (!RenderSystem.isOnRenderThread()) return original.call(window);
        EssentialScope.depth++;
        try {
            return original.call(window);
        } finally {
            EssentialScope.depth--;
        }
    }
}
