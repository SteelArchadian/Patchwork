package dev.chill.patchwork.mixin.ebwelcome;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Effortless Building greets every player once per world with a chat message. Its own switch for that is a
 * per-world server option, so there is no way to turn it off for good. With this module on the greeting is skipped
 * (the world just never records it as shown); turn the module off to get it back.
 */
@Pseudo
@Mixin(targets = "neoforge.nl.requios.effortlessbuilding.config.WelcomeMessageStorage", remap = false)
public abstract class WelcomeMessageMixin {
    @Inject(method = "showIfNeeded", require = 1, at = @At("HEAD"), cancellable = true)
    private static void patchwork$skip(ServerPlayer player, CallbackInfo ci) {
        ci.cancel();
    }
}
