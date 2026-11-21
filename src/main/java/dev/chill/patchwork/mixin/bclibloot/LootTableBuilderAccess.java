package dev.chill.patchwork.mixin.bclibloot;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootTable.Builder.class)
public interface LootTableBuilderAccess {
    @Accessor("pools")
    ImmutableList.Builder<LootPool> patchwork$pools();

    @Mutable
    @Accessor("pools")
    void patchwork$setPools(ImmutableList.Builder<LootPool> pools);
}
