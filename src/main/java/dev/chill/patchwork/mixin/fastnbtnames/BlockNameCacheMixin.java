package dev.chill.patchwork.mixin.fastnbtnames;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ConcurrentHashMap;

/**
 * FastNBT's block-state decoder parses the block name of every palette entry into a new ResourceLocation (two strings
 * and the location each time, plus a callback object per character check where Iris hooks the path test): 40-60 MB/s
 * while chunks load, for a few thousand distinct names. Each name is parsed once and the location reused; names that
 * don't parse aren't kept, so they still fail as before. ResourceLocation is immutable, so the answer is the same.
 */
@Pseudo
@Mixin(targets = "com.fast.fastnbt.blockstatecodec.FastBlockStateCodec", remap = false)
public abstract class BlockNameCacheMixin {
    /** Bounded so a world full of broken names can't grow it without limit; past it, names are parsed as before. */
    @Unique
    private static final int patchwork$MAX = 16384;
    @Unique
    private static final ConcurrentHashMap<String, ResourceLocation> patchwork$names = new ConcurrentHashMap<>();

    @Redirect(method = "decode", remap = false, require = 1, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/resources/ResourceLocation;tryParse(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation patchwork$cachedName(String name) {
        ResourceLocation known = patchwork$names.get(name);
        if (known != null) {
            return known;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(name);
        if (parsed != null && patchwork$names.size() < patchwork$MAX) {
            patchwork$names.putIfAbsent(name, parsed);
        }
        return parsed;
    }
}
