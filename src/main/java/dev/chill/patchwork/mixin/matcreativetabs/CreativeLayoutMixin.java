package dev.chill.patchwork.mixin.matcreativetabs;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory Expanded lifts the creative screen to y=54 and moves every widget up for its 3D mat in the Survival
 * Inventory tab, but never puts them back. After visiting that tab every other creative tab stayed lifted, with the
 * search box and side buttons (Vanilla Backport's too) out of place. The first layout is remembered per screen and
 * restored whenever Inventory Expanded drops its mat (tab switch, resize, close).
 */
@Pseudo
@Mixin(targets = "com.example.matinventory.screen.NativeMenuPreview", remap = false)
public abstract class CreativeLayoutMixin {
    @Unique
    private static final Map<AbstractContainerScreen<?>, Layout> patchwork$layouts = new WeakHashMap<>();

    @Shadow @Final private AbstractContainerScreen<?> screen;

    @Unique
    private record Layout(int top, Map<AbstractWidget, Integer> ys) {}

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "children()Ljava/util/List;"), require = 0)
    private void patchwork$remember(AbstractContainerScreen<?> s, CallbackInfo ci) {
        if (!(s instanceof CreativeModeInventoryScreen)) {
            return;
        }
        Layout layout = patchwork$layouts.computeIfAbsent(s, k -> new Layout(k.getGuiTop(), new WeakHashMap<>()));
        for (GuiEventListener child : s.children()) {
            if (child instanceof AbstractWidget w) {
                layout.ys().putIfAbsent(w, w.getY());
            }
        }
    }

    @Inject(method = "restore", at = @At("TAIL"), require = 0)
    private void patchwork$putBack(CallbackInfo ci) {
        Layout layout = patchwork$layouts.remove(screen);
        if (layout == null) {
            return;
        }
        int back = layout.top() - screen.getGuiTop();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w) {
                Integer y = layout.ys().get(w);
                // widgets added while lifted (other mods' buttons) move back by the same amount
                w.setY(y != null ? y : w.getY() + back);
            }
        }
        ((TopPosAccess) screen).patchwork$setTopPos(layout.top());
    }
}
