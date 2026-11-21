package dev.chill.patchwork.mixin.terrainslabs;

import net.countered.terrainslabs.generation.SlabFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * Terrain Slabs 3.1.2 checks the four horizontal neighbours of every bottom-slab candidate with a new iterator over
 * Direction.Plane.HORIZONTAL and four new BlockPos per side (neighbour, opposite, below, above): about 2.5% of all
 * allocation while chunks generate. This is the same check over a shared direction array with two reused mutable
 * positions. Same reads in the same order and the same answer, including the early "no" next to lava; the original
 * reads the block below the opposite side twice, which is read once here.
 */
@Mixin(value = SlabFeature.class, remap = false)
public abstract class SlabSurroundingMixin {
    @Unique
    private static final Direction[] patchwork$HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * @author Patchwork
     * @reason same check without the per-call iterator and BlockPos copies (an injector would add its own object)
     */
    @Overwrite
    private boolean validSurroundingBottom(WorldGenLevel level, BlockPos pos, boolean slabNeighbourOk) {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos other = new BlockPos.MutableBlockPos();
        boolean valid = false;
        for (Direction dir : patchwork$HORIZONTAL) {
            side.setWithOffset(pos, dir);
            other.setWithOffset(pos, dir.getOpposite()).move(Direction.DOWN);
            BlockState neighbour = level.getBlockState(side);
            BlockState oppositeBelow = level.getBlockState(other);
            if (neighbour.is(Blocks.LAVA)) {
                return false;
            }
            boolean neighbourSlab = neighbour.getBlock() instanceof SlabBlock;
            boolean neighbourSolid = !neighbour.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
            if (!((neighbourSolid && !neighbourSlab) || (neighbourSlab && slabNeighbourOk))) {
                continue;
            }
            if (oppositeBelow.getBlock() instanceof SlabBlock
                    || oppositeBelow.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
                continue;
            }
            if (level.getBlockState(side.move(Direction.UP)).getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
                valid = true;
            }
        }
        return valid;
    }
}
