package dev.chill.patchwork.mixin.stormiespiders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.chill.patchwork.stormiespiders.SpiderVerify;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.demistorm.stormiespiders.compat.sable.SubLevelPathing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Stormie's Spiders 3.3.2 picks the side a spider walks on every tick by asking for the collision boxes around it once
 * per direction: six block-collision scans per spider per tick, each building a fresh BlockCollisions, query shape and
 * moved copy of every nearby block shape (26% of all allocations in a spider-heavy Foundation profile).
 * <p>
 * The six query boxes all lie inside one box, so this scans that box once with the same BlockCollisions (other mods'
 * hooks into it still run) and keeps each climbable block it returns. Each direction then gets the blocks BlockCollisions
 * would have returned for its own box: same range, same edge rules, same overlap test, same order, same boxes. A query
 * outside the shared box (a changed Stormie's version) falls back to the original lookup.
 */
@Mixin(value = Spider.class, priority = 1100)
public abstract class SpiderWalkingSideMixin extends Monster {
    @Unique
    private static final double patchwork$EPS = 1.0E-7;

    /** Climbable blocks from this tick's shared scan, in BlockCollisions order; -1 until scanned this tick. */
    @Unique
    private int patchwork$count = -1;
    @Unique
    private int[] patchwork$pos = new int[48];
    @Unique
    private BlockState[] patchwork$states = new BlockState[16];
    @Unique
    private VoxelShape[] patchwork$shapes = new VoxelShape[16];
    @Unique
    private final List<AABB> patchwork$boxes = new ArrayList<>();
    @Unique
    private BiFunction<BlockPos.MutableBlockPos, VoxelShape, Object> patchwork$collect;
    @Unique
    private Shapes.DoubleLineConsumer patchwork$addBox;
    @Unique
    private final BlockPos.MutableBlockPos patchwork$cursor = new BlockPos.MutableBlockPos();

    protected SpiderWalkingSideMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Shadow(remap = false)
    public abstract boolean canClimbOnBlock(BlockState state, BlockPos pos);

    @Inject(method = "updateWalkingSide", at = @At("HEAD"), remap = false, require = 1)
    private void patchwork$newTick(CallbackInfo ci) {
        patchwork$count = -1;
    }

    @WrapOperation(method = "updateWalkingSide", remap = false, require = 1, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/entity/monster/Spider;getCollisionBoxes(Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<AABB> patchwork$sharedScan(Spider self, AABB query, Operation<List<AABB>> original) {
        if (SubLevelPathing.onSubLevel(self)) {
            return original.call(self, query);
        }
        // updateWalkingSide's boxes: the bounding box grown by 0.2f, then by 1.5f (moving) or 0.1f towards one side.
        double reach = this.zza != 0.0F ? 1.5F : 0.1F;
        AABB shared = getBoundingBox().inflate(0.2F).inflate(reach);
        if (query.minX < shared.minX || query.minY < shared.minY || query.minZ < shared.minZ
                || query.maxX > shared.maxX || query.maxY > shared.maxY || query.maxZ > shared.maxZ) {
            return original.call(self, query);
        }
        if (patchwork$count < 0) {
            patchwork$scan(shared);
        }
        List<AABB> boxes = patchwork$select(query);
        if (SpiderVerify.ON) {
            SpiderVerify.check(new ArrayList<>(boxes), original.call(self, query));
        }
        return boxes;
    }

    @Unique
    private void patchwork$scan(AABB shared) {
        if (patchwork$collect == null) {
            // Stormie's own result filter, recording each climbable block instead of listing its boxes.
            patchwork$collect = (pos, moved) -> {
                BlockState state = level().getBlockState(pos);
                if (canClimbOnBlock(state, pos)) {
                    int n = patchwork$count;
                    if (n == patchwork$states.length) {
                        patchwork$pos = Arrays.copyOf(patchwork$pos, n * 6);
                        patchwork$states = Arrays.copyOf(patchwork$states, n * 2);
                        patchwork$shapes = Arrays.copyOf(patchwork$shapes, n * 2);
                    }
                    patchwork$pos[n * 3] = pos.getX();
                    patchwork$pos[n * 3 + 1] = pos.getY();
                    patchwork$pos[n * 3 + 2] = pos.getZ();
                    patchwork$states[n] = state;
                    patchwork$shapes[n] = moved;
                    patchwork$count = n + 1;
                }
                return moved;
            };
        }
        // Drop last tick's blocks so they can be collected.
        if (patchwork$count > 0) {
            Arrays.fill(patchwork$states, 0, patchwork$count, null);
            Arrays.fill(patchwork$shapes, 0, patchwork$count, null);
        }
        patchwork$count = 0;
        BlockCollisions<Object> scan = new BlockCollisions<>(level(), this, shared, false, patchwork$collect);
        while (scan.hasNext()) {
            scan.next();
        }
    }

    /** What BlockCollisions over {@code query} would have returned, taken from the shared scan. */
    @Unique
    private List<AABB> patchwork$select(AABB query) {
        if (patchwork$addBox == null) {
            patchwork$addBox = (ax, ay, az, bx, by, bz) -> patchwork$boxes.add(new AABB(ax, ay, az, bx, by, bz));
        }
        patchwork$boxes.clear();
        // BlockCollisions' cursor runs one block past the box on every side; blocks on its edges follow its type rules.
        int x0 = Mth.floor(query.minX - patchwork$EPS) - 1, x1 = Mth.floor(query.maxX + patchwork$EPS) + 1;
        int y0 = Mth.floor(query.minY - patchwork$EPS) - 1, y1 = Mth.floor(query.maxY + patchwork$EPS) + 1;
        int z0 = Mth.floor(query.minZ - patchwork$EPS) - 1, z1 = Mth.floor(query.maxZ + patchwork$EPS) + 1;
        VoxelShape queryShape = null;
        for (int i = 0; i < patchwork$count; i++) {
            int x = patchwork$pos[i * 3], y = patchwork$pos[i * 3 + 1], z = patchwork$pos[i * 3 + 2];
            if (x < x0 || x > x1 || y < y0 || y > y1 || z < z0 || z > z1) {
                continue;
            }
            int type = (x == x0 || x == x1 ? 1 : 0) + (y == y0 || y == y1 ? 1 : 0) + (z == z0 || z == z1 ? 1 : 0);
            BlockState state = patchwork$states[i];
            if (type == 3 || (type == 1 && !state.hasLargeCollisionShape()) || (type == 2 && !state.is(Blocks.MOVING_PISTON))) {
                continue;
            }
            VoxelShape moved = patchwork$shapes[i];
            if (patchwork$fullBlock(state, moved, x, y, z)) {
                if (!query.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) {
                    continue;
                }
            } else {
                if (queryShape == null) {
                    queryShape = Shapes.create(query);
                }
                if (moved.isEmpty() || !Shapes.joinIsNotEmpty(moved, queryShape, BooleanOp.AND)) {
                    continue;
                }
            }
            moved.forAllBoxes(patchwork$addBox);
        }
        return patchwork$boxes;
    }

    /** A whole-block shape, which BlockCollisions tests with a plain box overlap. */
    @Unique
    private boolean patchwork$fullBlock(BlockState state, VoxelShape moved, int x, int y, int z) {
        return moved.min(Direction.Axis.X) == x && moved.max(Direction.Axis.X) == x + 1.0
                && moved.min(Direction.Axis.Y) == y && moved.max(Direction.Axis.Y) == y + 1.0
                && moved.min(Direction.Axis.Z) == z && moved.max(Direction.Axis.Z) == z + 1.0
                && state.isCollisionShapeFullBlock(level(), patchwork$cursor.set(x, y, z));
    }
}
