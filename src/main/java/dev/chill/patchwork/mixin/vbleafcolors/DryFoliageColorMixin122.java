package dev.chill.patchwork.mixin.vbleafcolors;

import com.blackgear.vanillabackport.client.api.modules.leaf_litter.DryLeafColorReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.OptionalInt;

/**
 * Vanilla Backport 1.2 moved LeafColors and reads biome overrides from DryLeafColorReloadListener, but still builds a
 * throwaway BlockTintCache for every leaf litter block (see DryFoliageColorMixin). Same fix for the new layout.
 */
@Pseudo
@Mixin(targets = "com.blackgear.vanillabackport.client.api.modules.falling_leaves.LeafColors", remap = false)
public abstract class DryFoliageColorMixin122 {
    @Shadow(remap = false) @Final public static ColorResolver DRY_FOLIAGE_COLOR_RESOLVER;

    /**
     * @author Patchwork
     * @reason the original wraps the same calculation in a throwaway tint cache on every call
     */
    @Overwrite(remap = false)
    public static int getAverageDryFoliageColor(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return -10732494;
        }
        OptionalInt override = DryLeafColorReloadListener.getColorForBiome(level.getBiome(pos));
        if (override.isPresent()) {
            return override.getAsInt();
        }
        return level.calculateBlockTint(pos, DRY_FOLIAGE_COLOR_RESOLVER);
    }
}
