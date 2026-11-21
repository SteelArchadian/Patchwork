package dev.chill.patchwork.mixin.fdloottable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Farmer's Delight 1.3.4 renamed the "lootTable" key of its farmersdelight:add_loot_table loot modifier to "table".
 * Addons built for older FD (Expanded Delight, Miner's Delight, My Nether's Delight, Cardiac...) still ship "lootTable",
 * so every one of their modifiers fails to load ("No key table") and their extra chest loot never appears. The codec
 * now reads "table" first and, when it is missing, "lootTable"; it always writes "table". Files that already use
 * "table" load exactly as before.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.loot.modifier.FDAddTableLootModifier", remap = false)
public abstract class LootTableKeyAliasMixin {
    @Unique private static final Logger patchwork$LOG = LoggerFactory.getLogger("Patchwork");
    @Unique private static volatile boolean patchwork$logged;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapOperation(method = "lambda$static$1", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;fieldOf(Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;", remap = false))
    private static MapCodec patchwork$aliasLootTable(Codec codec, String name, Operation<MapCodec> original) {
        MapCodec primary = original.call(codec, name);
        if (!"table".equals(name)) return primary;
        MapCodec<Either<Object, Object>> either = Codec.mapEither(primary, codec.fieldOf("lootTable"));
        return either.xmap(e -> {
            if (e.right().isPresent() && !patchwork$logged) {
                patchwork$logged = true;
                patchwork$LOG.info("[Patchwork] fdloottable: loot modifier with old \"lootTable\" key read as \"table\"");
            }
            return Either.unwrap((Either) e);
        }, Either::left);
    }
}
