package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.slf4j.Logger;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.leafpath=true (test copies only). Once the player
 * has been in the world for a while it builds a 5x5, two-high bush of persistent oak leaves 12 blocks east, spawns a
 * cow (no AI goals of its own) in the middle and tells it to walk to a spot 8 blocks past the bush. Every second it
 * logs where the cow is, whether it has a path and how far it turned; after 15 s "PASS" if the cow got out and within
 * 2 blocks of the spot, "FAIL" otherwise.
 */
public final class LeafPathTest {
    public static final boolean ON = Boolean.getBoolean("patchwork.test.leafpath");
    private static final int AT = Integer.getInteger("patchwork.test.leafpath.at", 200);
    private static final Logger LOG = LogUtils.getLogger();
    private static int worldTicks;
    private static int runTicks = -1;
    private static Cow cow;
    private static BlockPos goal;
    private static float lastYaw, turned;

    private LeafPathTest() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (mc.player == null || server == null || runTicks > 300) {
            return;
        }
        if (runTicks < 0) {
            if (++worldTicks == AT) {
                runTicks = 0;
                var id = mc.player.getUUID();
                server.execute(() -> setUp(server.getPlayerList().getPlayer(id).serverLevel(),
                        server.getPlayerList().getPlayer(id).blockPosition()));
            }
            return;
        }
        runTicks++;
        server.execute(LeafPathTest::step);
    }

    private static void setUp(ServerLevel level, BlockPos player) {
        BlockPos centre = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.east(12));
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    level.setBlockAndUpdate(centre.offset(dx, dy, dz), leaves);
                }
            }
        }
        goal = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.east(8));
        cow = EntityType.COW.create(level);
        cow.moveTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, 0, 0);
        cow.finalizeSpawn(level, level.getCurrentDifficultyAt(centre), MobSpawnType.COMMAND, null);
        cow.setNoAi(false);
        cow.goalSelector.removeAllGoals(g -> true);
        level.addFreshEntity(cow);
        lastYaw = cow.getYRot();
        LOG.info("[Patchwork/test] leafpath: cow in leaves at {}, goal {}, leaf cost {}", centre, goal,
                cow.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LEAVES));
    }

    private static void step() {
        if (cow == null || goal == null) {
            return;
        }
        if (cow.getNavigation().isDone()) {
            cow.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
        }
        turned += Math.abs(Mth.wrapDegrees(cow.getYRot() - lastYaw));
        lastYaw = cow.getYRot();
        double dist = Math.sqrt(cow.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5));
        if (runTicks % 20 == 0) {
            Path path = cow.getNavigation().getPath();
            LOG.info("[Patchwork/test] leafpath: t={} cow {} dist {} path {} turned {} deg", runTicks, cow.blockPosition(),
                    String.format("%.1f", dist), path == null ? "none" : path.getNodeCount() + " nodes", (int) turned);
        }
        if (runTicks == 300) {
            LOG.info("[Patchwork/test] leafpath: {} dist {} turned {} deg", dist <= 2.0 ? "PASS" : "FAIL",
                    String.format("%.1f", dist), (int) turned);
        }
    }
}
