package dev.chill.patchwork.mixin.lithiumnbt;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Loading a CompoundTag from disk or the network (chunk palettes, entities, block entities) fills its own map in
 * CompoundTag$1.loadCompound rather than going through new CompoundTag(). Vanilla's HashMap there made one Node per
 * key and resized as it grew; a fixed fastutil map sized for 4 keys still allocated two 9-slot arrays for every tag,
 * though most loaded tags (palette entries, block state properties) hold one or two keys. Chunk loading made that
 * about a tenth of all the game's allocation.
 * The entries are now read into a scratch map that each thread reuses, and the tag gets a map of exactly the size it
 * needs once the count is known: an array map for up to 8 keys (two arrays of that length, lookups compare a few
 * keys), a hash map sized for the count above that. Same keys and values; only the map type and its iteration order
 * differ, as they already did with Lithium's fastutil maps.
 */
@Mixin(targets = "net.minecraft.nbt.CompoundTag$1")
public abstract class CompoundLoadMapMixin {
    @Unique private static final int PATCHWORK$ARRAY_MAP_MAX = 8;
    /** Scratch maps per thread, one per nesting level in use (compounds load inside compounds). */
    @Unique private static final ThreadLocal<ArrayDeque<Object2ObjectOpenHashMap<String, Tag>>> PATCHWORK$SCRATCH =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** The vanilla HashMap is replaced below, so it is never made. */
    @Redirect(method = "loadCompound", require = 1,
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;", remap = false))
    private static HashMap<?, ?> patchwork$noHashMap() {
        return null;
    }

    @ModifyVariable(method = "loadCompound", require = 1, ordinal = 0,
            at = @At(value = "INVOKE_ASSIGN", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;", remap = false))
    private static Map<String, Tag> patchwork$scratch(Map<String, Tag> unused) {
        Object2ObjectOpenHashMap<String, Tag> scratch = PATCHWORK$SCRATCH.get().pollLast();
        return scratch != null ? scratch : new Object2ObjectOpenHashMap<>(16);
    }

    /** The finished entries go into a map of their own exact size; the scratch map goes back for the next tag. */
    @ModifyArg(method = "loadCompound", require = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;<init>(Ljava/util/Map;)V"))
    private static Map<String, Tag> patchwork$exactMap(Map<String, Tag> filled) {
        if (!(filled instanceof Object2ObjectOpenHashMap<String, Tag> scratch)) {
            return filled; // another mod supplied its own map: leave it as it is
        }
        int size = scratch.size();
        Map<String, Tag> exact;
        if (size <= PATCHWORK$ARRAY_MAP_MAX) {
            Object[] keys = new Object[size];
            Object[] values = new Object[size];
            int i = 0;
            // fastIterator reuses one entry object; the plain iterator made one per key.
            for (ObjectIterator<Object2ObjectMap.Entry<String, Tag>> it = scratch.object2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                Object2ObjectMap.Entry<String, Tag> entry = it.next();
                keys[i] = entry.getKey();
                values[i++] = entry.getValue();
            }
            exact = size == 0 ? new Object2ObjectArrayMap<>() : new Object2ObjectArrayMap<>(keys, values, size);
        } else {
            Object2ObjectOpenHashMap<String, Tag> sized = new Object2ObjectOpenHashMap<>(size);
            for (ObjectIterator<Object2ObjectMap.Entry<String, Tag>> it = scratch.object2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                Object2ObjectMap.Entry<String, Tag> entry = it.next();
                sized.put(entry.getKey(), entry.getValue());
            }
            exact = sized;
        }
        // A scratch map that once held a huge tag would make every later clear() walk its whole table.
        if (size > 64) {
            scratch.clear();
            scratch.trim(16);
        } else {
            scratch.clear();
        }
        PATCHWORK$SCRATCH.get().addLast(scratch);
        return exact;
    }
}
