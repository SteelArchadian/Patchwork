package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.deathcancel=true (test copies only). Once the
 * death screen has been up for 2 s it does what the Spawnstones button does (asks the server for the waystone list),
 * then presses Cancel on the list the way its button does, and logs which screen is showing for the next 3 s:
 * "PASS" if the death screen came back, "FAIL" otherwise.
 */
public final class DeathCancelTest {
    public static final boolean ENABLED = Boolean.getBoolean("patchwork.test.deathcancel");
    private static final int AT = Integer.getInteger("patchwork.test.deathcancel.at", 200);
    private static final Logger LOG = LogUtils.getLogger();
    private static int deathTicks, phaseTicks;
    private static int phase; // 0 wait for death, 1 waiting for list, 2 cancelled, 3 done
    private static int worldTicks;
    private static String lastScreen = "";

    private DeathCancelTest() {}

    private static String cinematicState() {
        try {
            Class<?> c = Class.forName("net.gamev.cinematic_respawn.client.RespawnCinematicController");
            var state = c.getDeclaredField("state");
            var ticks = c.getDeclaredField("stateTicks");
            state.setAccessible(true);
            ticks.setAccessible(true);
            return state.get(null) + "/" + ticks.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return e.toString();
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        String name = screen == null ? "none" : screen.getClass().getName();
        if (phase != 3 && !name.equals(lastScreen)) {
            LOG.info("[Patchwork/test] deathcancel: screen is now {}", name);
            lastScreen = name;
        }
        try {
            switch (phase) {
                case 0 -> {
                    // After 10 s in the world, die (a /kill there is cancelled by login protection while the player stands still).
                    if (mc.player != null && mc.getSingleplayerServer() != null && ++worldTicks == AT && !(screen instanceof DeathScreen)) {
                        var server = mc.getSingleplayerServer();
                        var id = mc.player.getUUID();
                        server.execute(() -> {
                            var player = server.getPlayerList().getPlayer(id);
                            if (player != null) {
                                // Login protection (logprot) cancels all damage until the player moves, and a test
                                // player never moves, so the death is applied directly: health 0, then the normal
                                // death (death message, drops, death screen packet).
                                player.setHealth(0.0F);
                                player.die(player.damageSources().genericKill());
                            }
                        });
                        LOG.info("[Patchwork/test] deathcancel: killed the player (alive={}, health={})", mc.player.isAlive(), mc.player.getHealth());
                    }
                    if (worldTicks > AT && worldTicks % 40 == 0 && mc.player != null) {
                        LOG.info("[Patchwork/test] deathcancel: {} s after kill: health={} dying={} cinematic={}", (worldTicks - AT) / 20,
                                mc.player.getHealth(), mc.player.isDeadOrDying(), cinematicState());
                    }
                    if (worldTicks == AT + 1000) {
                        LOG.info("[Patchwork/test] deathcancel: FAIL no death screen 60 s in (screen {})", name);
                        phase = 3;
                    }
                    if (screen instanceof DeathScreen && ++deathTicks == 40) {
                        Class<?> buttons = Class.forName("com.interfacelab.spawnstones.client.DeathScreenButtons");
                        Method request = buttons.getDeclaredMethod("requestChoices");
                        request.setAccessible(true);
                        request.invoke(null);
                        LOG.info("[Patchwork/test] deathcancel: asked for the waystone list");
                        phase = 1;
                    }
                }
                case 1 -> {
                    if (name.endsWith("WaystoneRespawnScreen")) {
                        LOG.info("[Patchwork/test] deathcancel: waystone list open, pressing Cancel");
                        screen.onClose();
                        phase = 2;
                        phaseTicks = 0;
                    } else if (++phaseTicks > 200) {
                        LOG.info("[Patchwork/test] deathcancel: FAIL waystone list never opened (screen {})", name);
                        phase = 3;
                    }
                }
                case 2 -> {
                    if (++phaseTicks % 20 == 0) {
                        LOG.info("[Patchwork/test] deathcancel: {} s after Cancel, screen is {}", phaseTicks / 20, name);
                    }
                    if (phaseTicks == 60) {
                        LOG.info("[Patchwork/test] deathcancel: {}", screen instanceof DeathScreen
                                ? "PASS back on the death screen" : "FAIL stuck on " + name);
                        if (screen instanceof DeathScreen && mc.player != null) {
                            // Then respawn as the death screen's Respawn button does, to check that still works.
                            mc.player.respawn();
                            LOG.info("[Patchwork/test] deathcancel: pressed Respawn");
                            phase = 4;
                            phaseTicks = 0;
                        } else {
                            phase = 3;
                        }
                    }
                }
                case 4 -> {
                    if (++phaseTicks == 300) {
                        boolean ok = mc.player != null && mc.player.isAlive() && !(screen instanceof DeathScreen);
                        LOG.info("[Patchwork/test] deathcancel: respawn {} (alive={}, screen {}, cinematic={})", ok ? "PASS" : "FAIL",
                                mc.player != null && mc.player.isAlive(), name, cinematicState());
                        phase = 3;
                    }
                }
                default -> {}
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.info("[Patchwork/test] deathcancel: FAIL {}", e.toString());
            phase = 3;
        }
    }
}
