package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.clusterrender=true (test copies only). Builds one
 * Relatively Placed Blocks cluster 3 blocks in front of the player with four small blocks in it (lantern, flower pot,
 * candle, grass), turns the player to look at it and saves cluster-render.png. Without the fix the spot is empty.
 */
public final class ClusterRenderTest {
    public static final boolean ON = Boolean.getBoolean("patchwork.test.clusterrender");
    private static final int AT = Integer.getInteger("patchwork.test.clusterrender.at", 100);
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;

    private ClusterRenderTest() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null || ticks > AT + 80) {
            return;
        }
        int t = ++ticks - AT;
        try {
            if (t == 0) {
                ServerLevel level = mc.getSingleplayerServer().overworld();
                BlockPos pos = mc.player.blockPosition().east(3);
                Block cluster = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("relativeblocks", "block_cluster"));
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        level.setBlockAndUpdate(pos, cluster.defaultBlockState());
                        BlockEntity be = level.getBlockEntity(pos);
                        Class<?> c = be.getClass();
                        c.getMethod("setLayout", int.class).invoke(be, 0);
                        BlockState[] members = {Blocks.LANTERN.defaultBlockState(), Blocks.FLOWER_POT.defaultBlockState(),
                                Blocks.CANDLE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState()};
                        for (int i = 0; i < members.length; i++) {
                            c.getMethod("setCell", int.class, BlockState.class).invoke(be, i, members[i]);
                        }
                        be.setChanged();
                        // A standalone vertical slab (mod 2.0+) one block to the side; missing on 1.x.
                        Block slab = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("relativeblocks", "vertical_slab/minecraft/oak_slab"));
                        if (slab != Blocks.AIR) {
                            level.setBlockAndUpdate(pos.south(), slab.defaultBlockState());
                        }
                        level.sendBlockUpdated(pos, cluster.defaultBlockState(), cluster.defaultBlockState(), 3);
                        LOG.info("[Patchwork/test] clusterrender: cluster at {} with {} members", pos, c.getMethod("memberCount").invoke(be));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        LOG.warn("[Patchwork/test] clusterrender: could not build the cluster", e);
                    }
                });
                // Look east and a little down at the cluster.
                mc.player.setYRot(-90.0F);
                mc.player.setXRot(25.0F);
            } else if (t == 60) {
                BlockPos pos = mc.player.blockPosition().east(3);
                for (BlockPos at : new BlockPos[] {pos, pos.south()}) {
                    BlockState state = mc.level.getBlockState(at);
                    var model = mc.getBlockRenderer().getBlockModel(state);
                    var data = model.getModelData(mc.level, at, state, mc.level.getModelData(at));
                    LOG.info("[Patchwork/test] clusterrender: {} model {} render types {}", state, model.getClass().getName(),
                            model.getRenderTypes(state, net.minecraft.util.RandomSource.create(), data));
                }
            } else if (t == 80) {
                Screenshot.grab(mc.gameDirectory, "cluster-render.png", mc.getMainRenderTarget(),
                        m -> LOG.info("[Patchwork/test] clusterrender: {}", m.getString()));
            }
        } catch (RuntimeException e) {
            LOG.warn("[Patchwork/test] clusterrender: step {} failed", t, e);
        }
    }
}
