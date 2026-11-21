package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.carry=&lt;block id&gt;,&lt;block id&gt;... (test
 * copies only, Carry On installed): once the player is in the world, makes them carry each block in turn through Carry
 * On's own data and saves a first-person and a third-person (back) screenshot of each, logging the file names.
 */
public final class CarryTest {
    public static final String BLOCKS = System.getProperty("patchwork.test.carry");
    private static final Logger LOG = LogUtils.getLogger();
    private static final int START = Integer.getInteger("patchwork.test.carry.start", 200);
    private static final int STEP = 60;
    private static int ticks;

    private CarryTest() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null || mc.screen != null) {
            return;
        }
        ticks++;
        if (ticks == 20) {
            mc.options.hideGui = true;
        }
        String[] ids = BLOCKS.split(",");
        int t = ticks - START;
        if (t < 0 || t > ids.length * STEP * 2) {
            return;
        }
        int slot = t / (STEP * 2);
        int within = t % (STEP * 2);
        if (slot >= ids.length) {
            if (within == 0) {
                LOG.info("[Patchwork/test] carry done");
            }
            return;
        }
        String id = ids[slot].trim();
        if (within == 0) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server.execute(() -> carry(server, id));
        } else if (within == STEP - 10) {
            shot(mc, id + " first-person");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (within == STEP * 2 - 10) {
            shot(mc, id + " third-person");
        }
    }

    private static void carry(MinecraftServer server, String id) {
        try {
            BlockState state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
            Object data = manager.getMethod("getCarryData", Player.class).invoke(null, player);
            data.getClass().getMethod("clear").invoke(data);
            data.getClass().getMethod("setBlock", BlockState.class, net.minecraft.world.level.block.entity.BlockEntity.class).invoke(data, state, null);
            manager.getMethod("setCarryData", Player.class, data.getClass()).invoke(null, player, data);
            player.containerMenu.broadcastChanges();
            LOG.info("[Patchwork/test] carry {}: carrying {}", id, state);
        } catch (Throwable t) {
            LOG.warn("[Patchwork/test] carry {} failed", id, t);
        }
    }

    private static void shot(Minecraft mc, String label) {
        Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                message -> LOG.info("[Patchwork/test] carry shot {}: {}", label, message.getString()));
    }
}
