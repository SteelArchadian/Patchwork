package dev.chill.patchwork.mixin.puzzlesholder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import fuzs.puzzleslib.api.config.v3.ConfigCore;
import fuzs.puzzleslib.api.config.v3.ConfigDataHolder;

import java.util.Map;

/**
 * Puzzles Lib (used by every Fuzs mod) looks up a mod's config holder with
 * {@code Objects.requireNonNull(holder, "No config holder available for type " + clazz)}, so the error text is built
 * on every lookup, even though it is only needed when the lookup fails. Easy Anvils does that lookup on every block
 * placed during world generation: about 19 MB/s of strings in a Test Dummy capture. Same lookup and same error; the
 * text is only built when there is no holder.
 */
@Pseudo
@Mixin(targets = "fuzs.puzzleslib.impl.config.ConfigHolderImpl", remap = false)
public abstract class ConfigHolderLookupMixin {
    @Shadow(remap = false) private Map<Class<?>, Object> configsByClass;

    /**
     * @author Patchwork
     * @reason build the "no holder" message only when the holder is missing
     */
    @Overwrite(remap = false)
    @SuppressWarnings("unchecked")
    public <T extends ConfigCore> ConfigDataHolder<T> getHolder(Class<T> clazz) {
        Object holder = configsByClass.get(clazz);
        if (holder == null) {
            throw new NullPointerException("No config holder available for type " + clazz);
        }
        return (ConfigDataHolder<T>) holder;
    }
}
