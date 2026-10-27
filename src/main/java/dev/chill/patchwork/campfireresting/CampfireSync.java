package dev.chill.patchwork.campfireresting;

import net.mcreator.campfireresting.network.CampfirerestingModVariables;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Campfire Resting player-variable sync, limited to clients that have the player loaded. Because updates no longer
 * reach players out of range, a player who comes into range is sent the other player's current state once.
 * Packet and save formats are Campfire Resting's own and unchanged.
 */
public final class CampfireSync {
    private CampfireSync() {}

    /** Call only when {@code Module.CAMPFIRE_RESTING.active()}. */
    public static void register() {
        NeoForge.EVENT_BUS.register(new CampfireSync());
    }

    public static void send(CampfirerestingModVariables.PlayerVariables variables, Entity player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new CampfirerestingModVariables.PlayerVariablesSyncMessage(variables, player.getId()));
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof ServerPlayer target
                && watcher.level() == target.level()) {
            PacketDistributor.sendToPlayer(watcher, new CampfirerestingModVariables.PlayerVariablesSyncMessage(
                    target.getData(CampfirerestingModVariables.PLAYER_VARIABLES), target.getId()));
        }
    }
}
