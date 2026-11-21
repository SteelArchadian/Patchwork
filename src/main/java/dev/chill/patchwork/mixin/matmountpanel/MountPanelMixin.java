package dev.chill.patchwork.mixin.matmountpanel;

import java.util.Map;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory Expanded keeps the top of a container screen down to its lowest container slot. Mods like Better Horses
 * park unused slots below the screen (y 166+), so llama/horse screens kept the whole panel, left the player area
 * blank and squeezed the 3D mat into a sliver at the bottom. Container slots at or below the player inventory are
 * not part of the top area; the cut is worked out again without them (only ever moves up, never down).
 */
@Pseudo
@Mixin(targets = "com.example.matinventory.screen.NativeMenuPreview", remap = false)
public abstract class MountPanelMixin {
    @Shadow @Final private AbstractContainerScreen<?> screen;
    @Shadow @Final private Map<Slot, ?> original;
    @Shadow @Final @Mutable private int cutY;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void patchwork$ignoreParkedSlots(AbstractContainerScreen<?> s, CallbackInfo ci) {
        if (screen instanceof CreativeModeInventoryScreen) return;
        int top = Integer.MAX_VALUE;
        for (Slot k : original.keySet()) {
            if (!(k.container instanceof net.minecraft.world.entity.player.Inventory)) continue;
            int idx = k.getContainerSlot();
            if (idx >= 9 && idx < 36) top = Math.min(top, k.y);
        }
        if (top == Integer.MAX_VALUE) return;
        int content = 24;
        for (Slot slot : screen.getMenu().slots) {
            if (original.containsKey(slot) || slot.x < 0 || slot.y < 0 || slot.y >= top) continue;
            content = Math.max(content, slot.y + 21);
        }
        int fixed = Math.max(content, Math.max(24, top - 12));
        if (fixed < cutY) cutY = fixed;
    }
}
