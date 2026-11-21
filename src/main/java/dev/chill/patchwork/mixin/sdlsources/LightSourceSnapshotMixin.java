package dev.chill.patchwork.mixin.sdlsources;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import toni.sodiumdynamiclights.DynamicLightSource;
import toni.sodiumdynamiclights.SodiumDynamicLights;

import java.util.Set;

/**
 * Sodium Dynamic Lights asks getDynamicLightLevel for every lit block while chunks are meshed. Each call took the
 * light-source read lock and walked the HashSet with a new iterator (about 1.7% of all allocation), and the render
 * thread, adding or removing a source under the write lock, had to wait for every meshing thread inside that loop
 * (stalls of 20-40 ms in Test Dummy captures).
 * The sources are now copied into an array by each writer, under the write lock, just before it releases it. Every
 * change to the set happens in one of the four methods below, so the array always matches the set as of the last
 * finished change. Readers read the array without a lock: same sources, same order, same light level, and a reader
 * racing a change sees the sources from just before or just after it, as it would have by taking the lock a moment
 * earlier or later.
 */
@Mixin(value = SodiumDynamicLights.class, remap = false)
public abstract class LightSourceSnapshotMixin {
    @Shadow @Final private Set<DynamicLightSource> dynamicLightSources;

    @Unique
    private volatile Object[] patchwork$sources = new Object[0];

    @Inject(method = {"addLightSource", "removeLightSource", "clearLightSources", "removeLightSources"}, require = 4,
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/ReentrantReadWriteLock$WriteLock;unlock()V"))
    private void patchwork$publish(CallbackInfo ci) {
        patchwork$sources = dynamicLightSources.toArray();
    }

    /**
     * @author Patchwork
     * @reason read the sources from the array the writers publish, without the lock or a new iterator per call
     */
    @Overwrite
    public double getDynamicLightLevel(BlockPos pos) {
        double level = 0.0;
        for (Object source : patchwork$sources) {
            level = SodiumDynamicLights.maxDynamicLightLevel(pos, (DynamicLightSource) source, level);
        }
        return Mth.clamp(level, 0.0, 15.0);
    }
}
