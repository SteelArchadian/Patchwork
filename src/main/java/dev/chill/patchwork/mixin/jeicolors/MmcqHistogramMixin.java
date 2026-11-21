package dev.chill.patchwork.mixin.jeicolors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * JEI's color search (colorSearchMode) quantizes every item's texture; each quantize starts with a fresh int[32768]
 * color histogram (128 KB), about 6% of in-world allocation on a low-end laptop. The histogram only lives for one
 * ColorThief.getPalette call on one thread (getPalette reads the palette before it returns), so each thread clears and
 * reuses its own. Same counting as JEI: 5 bits per channel, index (r << 10) + (g << 5) + b.
 */
@Mixin(targets = "mezz.jei.library.color.MMCQ", remap = false)
public abstract class MmcqHistogramMixin {
    @Unique
    private static final ThreadLocal<int[]> patchwork$HISTO = ThreadLocal.withInitial(() -> new int[1 << 15]);

    @Inject(method = "getHisto", at = @At("HEAD"), cancellable = true, require = 1)
    private static void patchwork$reuseHisto(int[][] pixels, CallbackInfoReturnable<int[]> cir) {
        int[] histo = patchwork$HISTO.get();
        Arrays.fill(histo, 0);
        for (int[] pixel : pixels) {
            histo[((pixel[0] >> 3) << 10) + ((pixel[1] >> 3) << 5) + (pixel[2] >> 3)]++;
        }
        cir.setReturnValue(histo);
    }
}
