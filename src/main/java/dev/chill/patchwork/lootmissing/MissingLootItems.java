package dev.chill.patchwork.lootmissing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A loot table that names one item from a mod that is not installed (or that a mod's own config switched off) fails to
 * load as a whole, so the chest, block or brushable it belongs to drops nothing at all: Integrated Dungeons and
 * Structures' Ars Nouveau and Ice and Fire chests, Spawn's anthills and others. Here such item entries are left out
 * before the table is read, and everything else in it still drops.
 */
public final class MissingLootItems {
    private static final Logger LOG = LogUtils.getLogger();

    private MissingLootItems() {}

    public static void prune(ResourceLocation table, JsonElement json) {
        List<String> removed = new ArrayList<>();
        walk(json, removed);
        if (!removed.isEmpty()) {
            LOG.info("[Patchwork] lootmissing: {} keeps working without {} (not installed)", table, String.join(", ", removed));
        }
    }

    private static void walk(JsonElement element, List<String> removed) {
        if (element instanceof JsonObject object) {
            for (var entry : object.entrySet()) {
                if (entry.getKey().equals("entries") || entry.getKey().equals("children")) {
                    if (entry.getValue() instanceof JsonArray entries) {
                        for (int i = entries.size() - 1; i >= 0; i--) {
                            String missing = missingItem(entries.get(i));
                            if (missing != null) {
                                entries.remove(i);
                                removed.add(missing);
                            }
                        }
                    }
                }
                walk(entry.getValue(), removed);
            }
        } else if (element instanceof JsonArray array) {
            for (JsonElement child : array) {
                walk(child, removed);
            }
        }
    }

    /** The item id of a plain item entry whose item is not registered, else null. */
    private static String missingItem(JsonElement entry) {
        if (!(entry instanceof JsonObject object) || !object.has("type") || !object.has("name")) {
            return null;
        }
        String type = object.get("type").getAsString();
        if (!type.equals("minecraft:item") && !type.equals("item")) {
            return null;
        }
        String name = object.get("name").getAsString();
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id != null && !BuiltInRegistries.ITEM.containsKey(id) ? name : null;
    }
}
