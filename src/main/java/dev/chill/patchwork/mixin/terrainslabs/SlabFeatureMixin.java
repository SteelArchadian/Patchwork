package dev.chill.patchwork.mixin.terrainslabs;

import com.mojang.logging.LogUtils;
import net.countered.terrainslabs.block.ModSlabsMap;
import net.countered.terrainslabs.generation.SlabFeature;
import net.countered.terrainslabs.platform.PlatformConfigHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Terrain Slabs 3.1.2 scans every block from the surface down to the bottom of the world in every column of every
 * chunk and, for each one, creates a new BlockPos plus above()/below() copies and reads up to three block states,
 * although almost every block fails the first test. This walks each column once from the top with a rolling
 * (above, current, below) window, so each block is read once and nothing is allocated, and calls Terrain Slabs' own
 * {@code shouldPlaceBottomSlab} / {@code shouldPlaceTopSlab} only where their first, necessary tests pass:
 * <ul>
 * <li>bottom slab needs: current has no collision or is a slab, the block above has no collision, and the block
 * below has a slab variant (exactly {@code isPosUpDownValid});</li>
 * <li>top slab needs: current is a full block, the block below is not, and the block above has a slab variant.</li>
 * </ul>
 * The scan only reads (slabs are placed after it), so the window sees the same states the original reads. The
 * resulting slab sets, and everything after the scan, are unchanged.
 *
 * With {@code -Dpatchwork.verify.terrainslabs=true} the original per-block decision is also computed and compared.
 */
@Mixin(value = SlabFeature.class, remap = false)
public abstract class SlabFeatureMixin {
    @Unique
    private static final Logger patchwork$LOG = LogUtils.getLogger();
    @Unique
    private static final boolean patchwork$VERIFY = Boolean.getBoolean("patchwork.verify.terrainslabs");
    @Unique
    private static final AtomicLong patchwork$checked = new AtomicLong();
    @Unique
    private static final AtomicLong patchwork$mismatches = new AtomicLong();

    @Shadow
    private boolean shouldPlaceBottomSlab(WorldGenLevel level, BlockPos currentPos, boolean isMaxY, boolean neighbourCanBeSlab) {
        throw new AssertionError();
    }

    @Shadow
    private boolean shouldPlaceTopSlab(WorldGenLevel level, BlockPos currentPos) {
        throw new AssertionError();
    }

    @Shadow
    private void placeBottomSlabs(WorldGenLevel level, Set<BlockPos> slabPositions) {
        throw new AssertionError();
    }

    @Shadow
    private void placeTopSlabs(WorldGenLevel level, Set<BlockPos> topSlabPositions) {
        throw new AssertionError();
    }

    @Shadow
    private void generateExtended(WorldGenLevel level, Set<BlockPos> botSlabPositions, Set<BlockPos> extendedPositions) {
        throw new AssertionError();
    }

    @Shadow
    private void calculateCornerPositions(WorldGenLevel level, Set<BlockPos> botSlabPositions, Set<BlockPos> cornerPositions) {
        throw new AssertionError();
    }

    /**
     * @author Chill (Patchwork)
     * @reason Same slab positions, without three allocations and up to three block reads per scanned block.
     */
    @Overwrite
    private void generateSlabs(WorldGenLevel level, BlockPos origin) {
        Set<BlockPos> botSlabPositions = new HashSet<>();
        Set<BlockPos> topSlabPositions = new HashSet<>();
        Set<BlockPos> extendedPositions = new HashSet<>();
        ChunkPos chunkPos = new ChunkPos(origin);
        int minY = level.getMinBuildHeight();
        int offsetXZ = PlatformConfigHooks.isCornerSlabsEnabled() ? 1 : 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -offsetXZ; x < 16 + offsetXZ; x++) {
            for (int z = -offsetXZ; z < 16 + offsetXZ; z++) {
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;
                int maxY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ);
                if (maxY < minY) {
                    continue;
                }

                BlockState above = level.getBlockState(cursor.set(worldX, maxY + 1, worldZ));
                BlockState current = level.getBlockState(cursor.set(worldX, maxY, worldZ));
                for (int y = maxY; y >= minY; y--) {
                    BlockState below = level.getBlockState(cursor.set(worldX, y - 1, worldZ));

                    boolean bottomPossible = (patchwork$noCollision(current) || current.getBlock() instanceof SlabBlock)
                            && patchwork$noCollision(above)
                            && ModSlabsMap.getSlabForBlock(below.getBlock()) != null;
                    boolean topPossible = patchwork$fullBlock(current)
                            && !patchwork$fullBlock(below)
                            && ModSlabsMap.getSlabForBlock(above.getBlock()) != null;

                    int decision = 0;
                    BlockPos currentPos = null;
                    if (bottomPossible || topPossible) {
                        currentPos = new BlockPos(worldX, y, worldZ);
                        if (bottomPossible && this.shouldPlaceBottomSlab(level, currentPos, y == maxY - 1, false)) {
                            decision = 1;
                            botSlabPositions.add(currentPos);
                        } else if (topPossible && this.shouldPlaceTopSlab(level, currentPos)) {
                            decision = 2;
                            topSlabPositions.add(currentPos);
                        }
                    }
                    if (patchwork$VERIFY) {
                        patchwork$verify(level, worldX, y, worldZ, y == maxY - 1, decision);
                    }

                    above = current;
                    current = below;
                }
            }
        }

        this.placeBottomSlabs(level, botSlabPositions);
        this.placeTopSlabs(level, topSlabPositions);
        if (PlatformConfigHooks.getSlabRunLength() > 1) {
            this.generateExtended(level, botSlabPositions, extendedPositions);
        } else if (PlatformConfigHooks.isCornerSlabsEnabled()) {
            this.calculateCornerPositions(level, botSlabPositions, extendedPositions);
            this.placeBottomSlabs(level, extendedPositions);
        }
    }

    @Unique
    private static boolean patchwork$noCollision(BlockState state) {
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    @Unique
    private static boolean patchwork$fullBlock(BlockState state) {
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Terrain Slabs' original per-block decision (0 none, 1 bottom, 2 top), computed the original way. */
    @Unique
    private void patchwork$verify(WorldGenLevel level, int x, int y, int z, boolean isMaxY, int decision) {
        BlockPos pos = new BlockPos(x, y, z);
        int original = this.shouldPlaceBottomSlab(level, pos, isMaxY, false) ? 1 : this.shouldPlaceTopSlab(level, pos) ? 2 : 0;
        long checked = patchwork$checked.incrementAndGet();
        if (original != decision) {
            long n = patchwork$mismatches.incrementAndGet();
            if (n <= 20) {
                patchwork$LOG.warn("[Patchwork/terrainslabs] mismatch #{} at {} {} {}: fast {} vs original {}", n, x, y, z, decision, original);
            }
        }
        if ((checked & ((1 << 24) - 1)) == 0) {
            patchwork$LOG.info("[Patchwork/terrainslabs] verify: {} blocks checked, {} mismatches", checked, patchwork$mismatches.get());
        }
    }
}
