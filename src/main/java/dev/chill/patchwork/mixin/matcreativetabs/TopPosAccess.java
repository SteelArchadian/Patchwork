package dev.chill.patchwork.mixin.matcreativetabs;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface TopPosAccess {
    @Accessor("topPos")
    void patchwork$setTopPos(int top);
}
