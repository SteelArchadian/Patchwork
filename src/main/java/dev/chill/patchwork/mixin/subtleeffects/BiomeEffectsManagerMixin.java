package dev.chill.patchwork.mixin.subtleeffects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.chill.patchwork.subtleeffects.SubtleCache;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Biome particles (spores, pollen, sculk dust): one biome lookup per block per tick instead of up to four. */
@Pseudo
@Mixin(targets = "einstein.subtle_effects.ticking.BiomeEffectsManager", remap = false)
public class BiomeEffectsManagerMixin {

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

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lme/fzzyhmstrs/fzzy_config/validation/ValidatedField;get()Ljava/lang/Object;"))
    private static Object patchwork$field(ValidatedField<?> field, Operation<Object> original) {
        return SubtleCache.field(field, original);
    }
}
