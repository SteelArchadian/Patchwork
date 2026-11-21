package dev.chill.patchwork.mixin.atlasbiomes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Antique Atlas picks a map texture for each biome from its tags. A biome with none of the tags it knows (cave biomes,
 * many modded biomes) got nothing: an error in the log for every one and a blank patch on the map. It now gets the
 * closest texture for its climate. Savanna biomes also asked for a texture set named "savana", which does not exist;
 * they get "savanna".
 */
@Pseudo
@Mixin(targets = "hunternif.mc.impl.atlas.client.TileTextureMap", remap = false)
public abstract class BiomeTextureGuessMixin {
    @Inject(method = "guessFittingTextureSetFallback", at = @At("HEAD"), cancellable = true, remap = false)
    private static void patchwork$byClimate(Biome biome, CallbackInfoReturnable<Optional<ResourceLocation>> cir) {
        if (biome == null) {
            return;
        }
        float temperature = biome.getBaseTemperature();
        float downfall = biome.getModifiedClimateSettings().downfall();
        String set;
        if (temperature < 0.15F) {
            set = "snow";
        } else if (!biome.hasPrecipitation() || downfall < 0.1F) {
            set = temperature >= 1.5F ? "desert" : "plains";
        } else if (temperature >= 0.95F && downfall >= 0.8F) {
            set = "jungle";
        } else if (downfall >= 0.85F) {
            set = "swamp";
        } else if (downfall >= 0.6F) {
            set = "forest";
        } else if (temperature >= 1.2F) {
            set = "savanna";
        } else {
            set = "plains";
        }
        cir.setReturnValue(Optional.of(ResourceLocation.fromNamespaceAndPath("antiqueatlas", set)));
    }

    @ModifyConstant(method = "guessFittingTextureSet", constant = @Constant(stringValue = "savana"), remap = false, require = 0)
    private static String patchwork$savannaName(String misspelt) {
        return "savanna";
    }
}
