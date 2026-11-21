package dev.chill.patchwork.mixin.openclstructblend;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * C2ME's OpenCL add-on generates biomes and noise for a 4x4 batch of chunks on the GPU. To upload structure
 * weights it hard-casts each chunk's beardifier to vanilla's Beardifier. StructBlend swaps that beardifier for its
 * own VillageUnderpinning or TerrainBeardifier near structures, so the cast fails and every chunk in the batch fails to load.
 *
 * Before the batch runs, each chunk's noise chunk is created the same way the batch does (it is cached on the
 * ProtoChunk, and the batch would create it next anyway). If any beardifier is neither empty nor vanilla's, the
 * batch is skipped. C2ME already skips a batch this way when blending applies; its chunks then go through the
 * normal per-chunk biome and noise steps on the CPU, where StructBlend's village underpinning runs as designed.
 */
@Pseudo
@Mixin(targets = "com.ishland.c2me.opts.accel.opencl.common.gen.CLServerBatchedBiomeNoiseContext", remap = false)
public abstract class BatchedNoiseBeardifierMixin {
    @Shadow(remap = false) @Final private ChunkPos startingPos;
    @Shadow(remap = false) @Final private NoiseBasedChunkGenerator generator;
    @Shadow(remap = false) @Final private RandomState noiseConfig;
    @Shadow(remap = false) @Final public static int BATCH_SIZE;

    @Unique
    private static final AtomicBoolean patchwork$logged = new AtomicBoolean();

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void patchwork$cpuForCustomBeardifier(CallbackInfoReturnable<CompletableFuture<Void>> cir,
                                                  @Local(argsOnly = true, ordinal = 0) StaticCache2D<ProtoChunk> chunks,
                                                  @Local(argsOnly = true, ordinal = 1) StaticCache2D<StructureManager> structures) {
        for (int dx = 0; dx < BATCH_SIZE; dx++) {
            for (int dz = 0; dz < BATCH_SIZE; dz++) {
                int x = startingPos.x + dx;
                int z = startingPos.z + dz;
                ProtoChunk chunk = chunks.get(x, z);
                if (chunk == null || chunk.getPersistedStatus().isOrAfter(ChunkStatus.NOISE)) {
                    continue;
                }
                NoiseChunk noise = chunk.getOrCreateNoiseChunk(c -> generator.createNoiseChunk(c, structures.get(x, z),
                        Blender.empty(), noiseConfig));
                DensityFunctions.BeardifierOrMarker beardifier = noise.beardifier;
                if (beardifier != DensityFunctions.BeardifierMarker.INSTANCE && !(beardifier instanceof Beardifier)) {
                    if (patchwork$logged.compareAndSet(false, true)) {
                        LoggerFactory.getLogger("Patchwork").info("[Patchwork/openclstructblend] chunks with {} go "
                                + "through C2ME's CPU path (first at {}, {})", beardifier.getClass().getName(), x, z);
                    }
                    cir.setReturnValue(CompletableFuture.completedFuture(null));
                    return;
                }
            }
        }
    }
}
