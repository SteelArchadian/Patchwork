package dev.chill.patchwork.mixin.lithiumnbt;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

/**
 * Lithium's alloc.nbt mixin gives every new CompoundTag a fastutil map at the default size: two 33-slot arrays up
 * front, although nearly every tag (block-state palette entries, scheduled ticks, list entries on chunk load) holds a
 * handful of keys. On Test Dummy that was 20% of all allocation, mostly on the chunk save/load workers.
 * Patchwork turns that one Lithium option off (neoforge.mods.toml, "lithium:options") and makes the same map here,
 * sized for 4 keys; it grows like any fastutil map when a tag holds more. Same map type, same contents.
 */
@Mixin(CompoundTag.class)
public abstract class CompoundTagSizeMixin {
    /** The vanilla HashMap is replaced below, so it is never made. */
    @Redirect(method = "<init>()V", require = 1,
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;", remap = false))
    private static HashMap<?, ?> patchwork$noHashMap() {
        return null;
    }

    @ModifyArg(method = "<init>()V", require = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;<init>(Ljava/util/Map;)V"))
    private static Map<String, Tag> patchwork$smallMap(Map<String, Tag> unused) {
        return new Object2ObjectOpenHashMap<>(4);
    }
}
