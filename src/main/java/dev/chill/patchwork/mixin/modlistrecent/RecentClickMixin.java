package dev.chill.patchwork.mixin.modlistrecent;

import dev.chill.patchwork.modlistrecent.RecentMods;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clicking a mod in the mod list counts as using it for the Recent sort (arrowing past it does not). */
@Mixin(value = ModListWidget.ModEntry.class, remap = false)
public abstract class RecentClickMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), require = 1)
    private void patchwork$clicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        RecentMods.touch(((ModListWidget.ModEntry) (Object) this).getInfo().getModId());
    }
}
