package dev.chill.patchwork.mixin.campfireresting;

import dev.chill.patchwork.campfireresting.CampfireSync;
import net.mcreator.campfireresting.network.CampfirerestingModVariables;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Campfire Resting 1.6.0 sent every player's variables to every player in the dimension, loaded or not. A player's
 * update now goes to that player and the players tracking them, the only clients that have the entity to apply it to.
 */
@Mixin(value = CampfirerestingModVariables.PlayerVariables.class, remap = false)
public abstract class PlayerVariablesMixin {

    @Inject(method = "syncPlayerVariables", at = @At("HEAD"), cancellable = true)
    private void patchwork$sendToTrackers(Entity entity, CallbackInfo ci) {
        if (!entity.level().isClientSide() && entity instanceof ServerPlayer) {
            CampfireSync.send((CampfirerestingModVariables.PlayerVariables) (Object) this, entity);
            ci.cancel();
        }
    }
}
