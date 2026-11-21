package dev.chill.patchwork.mixin.vbleafcolors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Vanilla Backport colours leaf litter per block with getAverageDryFoliageColor. When no biome override applies it
 * builds a brand-new BlockTintCache (a Long2ObjectLinkedOpenHashMap plus its lambdas) for that one lookup, which
 * always misses and is thrown away: about 3% of in-world allocation, from chunk meshing. The cache never returns
 * anything but ClientLevel.calculateBlockTint for the same position and resolver, so that is called directly. The
 * biome overrides are checked with a plain loop over the same map instead of a stream.
 */
@Pseudo
@Mixin(targets = "com.blackgear.vanillabackport.client.api.color.LeafColors", remap = false)
public abstract class DryFoliageColorMixin {
    @Shadow @Final private static Map<Predicate<Holder<Biome>>, Integer> COLOR_MAP;
    @Shadow @Final public static ColorResolver DRY_FOLIAGE_COLOR_RESOLVER;

    /**
     * @author Patchwork
     * @reason the original wraps the same calculation in a throwaway tint cache on every call
     */
    @Overwrite
    public static int getAverageDryFoliageColor(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return -10732494;
        }
        Holder<Biome> biome = level.getBiome(pos);
        for (Map.Entry<Predicate<Holder<Biome>>, Integer> entry : COLOR_MAP.entrySet()) {
            if (entry.getKey().test(biome)) {
                return entry.getValue();
            }
        }
        return level.calculateBlockTint(pos, DRY_FOLIAGE_COLOR_RESOLVER);
    }
}
