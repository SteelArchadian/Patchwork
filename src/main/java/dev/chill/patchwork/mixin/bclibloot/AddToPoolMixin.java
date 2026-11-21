package dev.chill.patchwork.mixin.bclibloot;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * BCLib's LootUtil.addToPool looks for a List field of pools on the loot table builder, but Minecraft keeps them in an
 * ImmutableList.Builder, so it never finds one: Better Nether's extra piglin barters (blue obsidian, weeping obsidian)
 * were never added and "Unable to building bartering table" was logged. Here the entries are appended to the pool
 * the builder really holds.
 */
@Pseudo
@Mixin(targets = "org.betterx.bclib.util.LootUtil", remap = false)
public abstract class AddToPoolMixin {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean logged;

    @Inject(method = "addToPool", at = @At("HEAD"), cancellable = true, remap = false)
    private static void patchwork$addToPool(LootTable.Builder builder, int index, ArrayList<LootPoolEntryContainer> entries,
                                            CallbackInfoReturnable<Boolean> cir) {
        List<LootPool> pools = ((LootTableBuilderAccess) builder).patchwork$pools().build();
        if (index < 0 || index >= pools.size()) {
            LOG.warn("[Patchwork] bclibloot: no pool {} to add to ({} pools)", index, pools.size());
            return;
        }
        LootPoolAccess pool = (LootPoolAccess) pools.get(index);
        pool.patchwork$setEntries(ImmutableList.<LootPoolEntryContainer>builder()
                .addAll(pool.patchwork$entries()).addAll(entries).build());
        if (!logged) {
            logged = true;
            LOG.info("[Patchwork] bclibloot: added {} BetterX loot entries to an existing table", entries.size());
        }
        cir.setReturnValue(true);
    }
}
