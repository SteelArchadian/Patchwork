package dev.chill.patchwork.mixin.particlerainids;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Particle Rain picks a sprite for every rain, snow and dust particle it spawns by parsing that sprite's id string
 * into a ResourceLocation again (a validity check of every character each time). The ids come from the particle
 * styles' config, a few dozen strings, so each is parsed once here and reused. Same ids, same sprites; an id that
 * does not parse stays "no id", as before.
 */
@Pseudo
@Mixin(targets = "pigcart.particlerain.particle.CustomParticle", remap = false)
public abstract class SpriteIdCacheMixin {
    @Unique
    private static final Map<String, Optional<ResourceLocation>> PATCHWORK$IDS = new ConcurrentHashMap<>();

    @Redirect(method = "pickSprite", require = 1,
            at = @At(value = "INVOKE", target = "Lpigcart/particlerain/VersionUtil;parseId(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation patchwork$cachedId(String id) {
        // Config ids only; the cap just guards against a mod feeding endless distinct strings through here.
        Optional<ResourceLocation> cached = PATCHWORK$IDS.get(id);
        if (cached == null) {
            cached = Optional.ofNullable(ResourceLocation.tryParse(id));
            if (PATCHWORK$IDS.size() < 4096) {
                PATCHWORK$IDS.put(id, cached);
            }
        }
        return cached.orElse(null);
    }
}
