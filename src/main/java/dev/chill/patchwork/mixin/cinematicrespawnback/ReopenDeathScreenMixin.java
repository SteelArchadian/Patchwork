package dev.chill.patchwork.mixin.cinematicrespawnback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cinematic Respawn holds the death screen back while another screen is open (a player who dies with the inventory
 * open sees the death screen once they close it). A screen opened from the death screen itself, such as Spawnstones'
 * waystone picker, counts as such a screen too, so its Cancel button (and Escape) went nowhere: the death screen it
 * returns to was held back for good and the player was stuck. A death screen that was already shown once is never
 * held back now; a new one still is. Works for any mod's screen that returns to the death screen.
 */
@Pseudo
@Mixin(targets = "net.gamev.cinematic_respawn.client.RespawnScreens", remap = false)
public abstract class ReopenDeathScreenMixin {
    @Inject(method = "interceptScreenChange", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void patchwork$letDeathScreenBack(Minecraft minecraft, Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof DeathScreen && ((ScreenAccessor) screen).patchwork$minecraft() != null) {
            cir.setReturnValue(false);
        }
    }
}
