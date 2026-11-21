package dev.chill.patchwork.mixin.matvbcreative;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inventory Expanded decides "side widgets present" by looking at every child widget, hidden ones too. Vanilla
 * Backport adds its bundled-tab buttons left of the creative panel and hides them outside its own tab, so the 3D mat
 * was pushed below the screen in the Survival Inventory tab. Only widgets that are actually shown count now.
 * Buttons parked along the bottom edge of the screen (below where the mat ends, height - 50) never touch the mat
 * either, yet they made mount screens (llama, horse) keep their full panel with a blank player area and squeeze the
 * mat into a sliver at the bottom. They no longer count.
 */
@Pseudo
@Mixin(targets = "com.example.matinventory.screen.NativeMenuPreview", remap = false)
public abstract class SidebarsMixin {
    @Shadow @Final private AbstractContainerScreen<?> screen;
    @Shadow @Final private int cutY;
    @Shadow @Final private int playerX;
    @Shadow @Final private int playerWidth;

    @Inject(method = "sidebars", at = @At("HEAD"), cancellable = true, require = 0)
    private void patchwork$visibleSidebars(CallbackInfoReturnable<Boolean> cir) {
        int l = screen.getGuiLeft() + playerX;
        int r = l + playerWidth;
        int b = screen.getGuiTop() + cutY;
        cir.setReturnValue(screen.children().stream().anyMatch(w -> w instanceof AbstractWidget v && v.visible
                && v.getY() + v.getHeight() > b && v.getY() < screen.height - 50 && (v.getX() < l || v.getX() + v.getWidth() > r)));
    }
}
