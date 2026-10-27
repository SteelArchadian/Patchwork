package dev.chill.patchwork.mixin.subtleeffects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.chill.patchwork.subtleeffects.SubtleCache;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Geysers: the spawnable-block config list as a set. Only the config check; the live geyser position lists are left alone. */
@Pseudo
@Mixin(targets = "einstein.subtle_effects.ticking.tickers.geyser.GeyserTicker", remap = false)
public class GeyserTickerMixin {

    @WrapOperation(method = "isSpawnableBlock", at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private static boolean patchwork$contains(List<?> list, Object element, Operation<Boolean> original) {
        return SubtleCache.contains(list, element, original);
    }

    @WrapOperation(method = "isSpawnableBlock", at = @At(value = "INVOKE", target = "Lme/fzzyhmstrs/fzzy_config/validation/ValidatedField;get()Ljava/lang/Object;"))
    private static Object patchwork$field(ValidatedField<?> field, Operation<Object> original) {
        return SubtleCache.field(field, original);
    }
}
