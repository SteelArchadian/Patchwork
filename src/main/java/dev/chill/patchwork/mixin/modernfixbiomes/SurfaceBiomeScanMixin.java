package dev.chill.patchwork.mixin.modernfixbiomes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * ModernFix's optimize_surface_rules collects the biomes near each generating chunk. For a biome section with more
 * than one biome it calls PalettedContainer.getAll, which walks all 64 entries into a new IntArraySet and boxes a
 * lambda per section: about 2.5% of all allocation while chunks generate. The section's palette already lists those
 * biomes, so it is read directly instead. A palette can still hold a biome no cell uses any more (after /fillbiome);
 * that only adds a candidate biome, and ModernFix uses the set to skip rules for biomes that cannot occur, so the
 * terrain is unchanged. The global palette (every registered biome) still goes through getAll.
 *
 * mfix$accumulate is ModernFix's own mixin method, so this applies after it (priority 1100). require = 0 because
 * the method is absent when ModernFix's optimize_surface_rules option is off; the module's version gate covers the
 * rest.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, priority = 1100)
public abstract class SurfaceBiomeScanMixin {
    @WrapOperation(method = "mfix$accumulate", remap = false, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/PalettedContainerRO;getAll(Ljava/util/function/Consumer;)V"))
    private static <T> void patchwork$readPalette(PalettedContainerRO<T> container, Consumer<T> consumer,
                                                  Operation<Void> original, @Local Palette<T> palette) {
        if (palette instanceof GlobalPalette) {
            original.call(container, consumer);
            return;
        }
        for (int i = 0, size = palette.getSize(); i < size; i++) {
            consumer.accept(palette.valueFor(i));
        }
    }
}
