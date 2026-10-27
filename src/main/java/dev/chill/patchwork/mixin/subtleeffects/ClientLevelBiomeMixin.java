package dev.chill.patchwork.mixin.subtleeffects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.chill.patchwork.subtleeffects.SubtleCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla's ambient-particle biome lookup for each animated block shares the same per-tick answer Subtle Effects just looked up. */
@Mixin(ClientLevel.class)
public class ClientLevelBiomeMixin {

    @WrapOperation(method = "doAnimateTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> patchwork$biome(ClientLevel level, BlockPos pos, Operation<Holder<Biome>> original) {
        return SubtleCache.biome(level, pos, original);
    }
}
