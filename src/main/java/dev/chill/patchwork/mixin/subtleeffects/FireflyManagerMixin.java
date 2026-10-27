package dev.chill.patchwork.mixin.subtleeffects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.chill.patchwork.subtleeffects.SubtleCache;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Fireflies: skip blocks they can never appear in, and reuse this tick's biome, config conditions and list lookups. */
@Pseudo
@Mixin(targets = "einstein.subtle_effects.ticking.FireflyManager", remap = false)
public class FireflyManagerMixin {

    /**
     * A firefly can only appear in a block that is not a full solid block and holds no fluid; Subtle Effects checks that last,
     * after the biome, heightmap and sky lookups. Every check before it only reads the world, so testing it first gives the
     * same result for every solid or water block, without the lookups.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void patchwork$skipSolid(Level level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo ci) {
        boolean skip = SubtleCache.enabled() && (state.isCollisionShapeFullBlock(level, pos) || !level.getFluidState(pos).isEmpty());
        if (SubtleCache.VERIFY) {
            patchwork$wouldSkip = skip;   // run the original anyway; anything it spawns for a skipped block is a mismatch
        } else if (skip) {
            ci.cancel();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean patchwork$wouldSkip;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private static void patchwork$verifyParticle(Level level, net.minecraft.core.particles.ParticleOptions particle, double x, double y, double z,
                                                double dx, double dy, double dz, Operation<Void> original) {
        if (SubtleCache.VERIFY) {
            SubtleCache.check("firefly early-out (particle)", !patchwork$wouldSkip, BlockPos.containing(x, y, z));
        }
        original.call(level, particle, x, y, z, dx, dy, dz);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private static Holder<Biome> patchwork$biome(Level level, BlockPos pos, Operation<Holder<Biome>> original) {
        return SubtleCache.biome(level, pos, original);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lme/fzzyhmstrs/fzzy_config/validation/misc/ValidatedCondition;get()Ljava/lang/Object;"))
    private static Object patchwork$condition(ValidatedCondition<?> condition, Operation<Object> original) {
        return SubtleCache.condition(condition, original);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private static boolean patchwork$contains(List<?> list, Object element, Operation<Boolean> original) {
        return SubtleCache.contains(list, element, original);
    }
}
