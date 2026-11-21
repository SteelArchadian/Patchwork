package dev.chill.patchwork.relativeblocks;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Olaf's Relatively Placed Blocks (a Fabric mod run through Connector) hooks the vanilla getSoundType() call in
 * MultiPlayerGameMode.continueDestroyBlock to play the sound of the small block under the crosshair. NeoForge calls
 * getSoundType(level, pos, entity) there instead, so the hook finds nothing and Connector stops the game from starting.
 * {@link MemberSoundRewrite} turns that call into {@link #remember} + a plain getSoundType() call (the mod's hook now
 * has its target) + {@link #pick}. The sound that plays is the one {@link #remember} worked out: NeoForge's own, or
 * the aimed-at member's for a cluster.
 */
public final class MemberSound {
    private static final Logger LOG = LogUtils.getLogger();
    private static SoundType remembered;
    private static Block cluster;
    private static Class<?> clusterEntity;
    private static Method cellUnderCrosshair, getCell;
    private static boolean looked, broken;

    private MemberSound() {}

    /** Called in place of NeoForge's getSoundType: works out the sound, hands the state on to the plain call. */
    public static BlockState remember(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        remembered = memberSound(state, level, pos, entity);
        return state;
    }

    /** Called on the plain getSoundType()'s result (possibly the mod's): the sound worked out in {@link #remember}. */
    public static SoundType pick(SoundType plain) {
        SoundType sound = remembered;
        remembered = null;
        return sound != null ? sound : plain;
    }

    private static SoundType memberSound(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        if (lookUp() && state.is(cluster)) {
            try {
                BlockEntity be = level.getBlockEntity(pos);
                if (clusterEntity.isInstance(be)) {
                    int cell = (Integer) cellUnderCrosshair.invoke(null, pos);
                    if (cell >= 0 && getCell.invoke(be, cell) instanceof BlockState member) {
                        return member.getSoundType(level, pos, entity);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                fail(e);
            }
        }
        return state.getSoundType(level, pos, entity);
    }

    private static boolean lookUp() {
        if (!looked) {
            looked = true;
            try {
                Field field = Class.forName("com.lukeolafp.relativeblocks.init.BlockInit").getField("BLOCK_CLUSTER");
                cluster = (Block) field.get(null);
                clusterEntity = Class.forName("com.lukeolafp.relativeblocks.blocks.BlockClusterBlockEntity");
                getCell = clusterEntity.getMethod("getCell", int.class);
                cellUnderCrosshair = Class.forName("com.lukeolafp.relativeblocks.blocks.ClusterMemberFocus")
                        .getMethod("cellUnderCrosshair", BlockPos.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                fail(e);
            }
        }
        return !broken && cluster != null;
    }

    /** Anything unexpected: log once and fall back to the block's own sound for good. */
    private static void fail(Throwable e) {
        if (!broken) {
            broken = true;
            LOG.warn("[Patchwork] relativeblocks: cluster member sounds off ({})", e.toString());
        }
    }
}
