package dev.chill.patchwork.mixin.cullreset;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Entity Culling clears its whole occlusion cache before every culling pass: a byte per four blocks of the tracing cube
 * (4 MB at the default range), zeroed with Arrays.fill although a pass only marks a few thousand cells. Each cell is
 * now remembered the first time a pass marks it, and the reset zeroes just those. A pass that marks more than an eighth
 * of the cache clears it all, as before. Every cell is zero again after the reset, so the next pass sees the same cache.
 */
@Pseudo
@Mixin(targets = {"com.logisticscraft.occlusionculling.cache.ArrayOcclusionCache",
        "dev.tr7zw.entityculling.versionless.occlusionculling.cache.ArrayOcclusionCache"}, remap = false)
public abstract class DirtyResetMixin {
    @Shadow(remap = false) @Final private byte[] cache;
    @Shadow(remap = false) private int entry;

    @Unique
    private int[] patchwork$dirty = new int[1024];
    @Unique
    private int patchwork$dirtyCount;
    @Unique
    private boolean patchwork$overflow;

    @Inject(method = {"setVisible", "setHidden", "setLastVisible", "setLastHidden"}, at = @At("TAIL"), remap = false, require = 4)
    private void patchwork$mark(CallbackInfo ci) {
        if (patchwork$overflow) {
            return;
        }
        int e = entry;
        // A cell is recorded when this write is its first bit since the reset (the cell now holds only one bit set).
        int v = cache[e] & 0xFF;
        if ((v & (v - 1)) != 0) {
            return;
        }
        int n = patchwork$dirtyCount;
        if (n == patchwork$dirty.length) {
            if (n * 2 > cache.length / 8) {
                patchwork$overflow = true;
                return;
            }
            patchwork$dirty = Arrays.copyOf(patchwork$dirty, n * 2);
        }
        patchwork$dirty[n] = e;
        patchwork$dirtyCount = n + 1;
    }

    /**
     * @author Patchwork
     * @reason zero only the cells the last pass marked instead of the whole cache
     */
    @Overwrite(remap = false)
    public void resetCache() {
        if (patchwork$overflow) {
            Arrays.fill(cache, (byte) 0);
        } else {
            int[] dirty = patchwork$dirty;
            for (int i = 0, n = patchwork$dirtyCount; i < n; i++) {
                cache[dirty[i]] = 0;
            }
        }
        patchwork$dirtyCount = 0;
        patchwork$overflow = false;
    }
}
