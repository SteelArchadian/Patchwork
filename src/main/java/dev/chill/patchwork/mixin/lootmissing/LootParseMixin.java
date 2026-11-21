package dev.chill.patchwork.mixin.lootmissing;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import dev.chill.patchwork.lootmissing.MissingLootItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Leaves out loot entries for items that do not exist before a table is read; see {@link MissingLootItems}. */
@Mixin(LootDataType.class)
public abstract class LootParseMixin {
    @Inject(method = "deserialize", at = @At("HEAD"))
    private <V> void patchwork$pruneMissing(ResourceLocation id, DynamicOps<V> ops, V value, CallbackInfoReturnable<Optional<?>> cir) {
        if (value instanceof JsonElement json) {
            MissingLootItems.prune(id, json);
        }
    }
}
