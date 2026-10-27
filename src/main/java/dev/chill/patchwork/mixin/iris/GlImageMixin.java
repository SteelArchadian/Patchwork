package dev.chill.patchwork.mixin.iris;

import dev.chill.patchwork.iris.ImageZeroer;
import net.irisshaders.iris.gl.image.GlImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris 1.8.14-beta.1 allocates shader-pack custom images ({@code image.*} in shaders.properties) with no initial data,
 * so their contents are whatever was in that VRAM before. Images the pack declares with clear=false are never cleared
 * by Iris afterwards, and packs that accumulate into them (Bliss's light-propagation volumes) then show the leftovers:
 * a strong yellow-green glow near the camera when the pipeline is built at world join. This zero-fills such images
 * once, right after Iris allocates or resizes them. Images with clear=true are left alone (Iris clears those every
 * frame), and nothing changes after the first fill.
 */
@Mixin(value = GlImage.class, remap = false)
public abstract class GlImageMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void patchwork$zeroAfterCreate(CallbackInfo ci) {
        patchwork$zero();
    }

    @Inject(method = "updateNewSize", at = @At("RETURN"))
    private void patchwork$zeroAfterResize(int width, int height, CallbackInfo ci) {
        patchwork$zero();
    }

    @org.spongepowered.asm.mixin.Unique
    private void patchwork$zero() {
        GlImage self = (GlImage) (Object) this;
        if (!self.shouldClear()) {
            ImageZeroer.zero(self.getId(), self.getFormat().getGlFormat(), self.getPixelType().getGlFormat(), self.getName());
        }
    }
}
