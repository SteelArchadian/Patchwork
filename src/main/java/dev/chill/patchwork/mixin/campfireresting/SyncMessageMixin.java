package dev.chill.patchwork.mixin.campfireresting;

import net.mcreator.campfireresting.network.CampfirerestingModVariables;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Campfire Resting 1.6.0 applies a player-variables update to {@code level.getEntity(target)} without checking that
 * the client has that player loaded. Updates for a player out of range, in another dimension or mid-respawn threw a
 * NullPointerException each time (83,528 in one logged session), and the handler's error path tries to disconnect.
 * Such an update is now dropped; the client gets the current state when it starts tracking that player
 * ({@link dev.chill.patchwork.campfireresting.CampfireSync}).
 */
@Mixin(value = CampfirerestingModVariables.PlayerVariablesSyncMessage.class, remap = false)
public abstract class SyncMessageMixin {

    @Inject(method = "lambda$handleData$2", at = @At("HEAD"), cancellable = true)
    private static void patchwork$dropUnknownTarget(IPayloadContext context,
            CampfirerestingModVariables.PlayerVariablesSyncMessage message, CallbackInfo ci) {
        Player player = context.player();
        if (player == null || player.level().getEntity(message.target()) == null) {
            ci.cancel();
        }
    }
}
