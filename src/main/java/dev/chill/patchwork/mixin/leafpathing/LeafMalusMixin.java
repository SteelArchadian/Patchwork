package dev.chill.patchwork.mixin.leafpathing;

import com.dreu.traversableleaves.interfaces.ITraversableBlock;
import com.dreu.traversableleaves.interfaces.ITraversableEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Traversable Leaves 2.1.1 lets mobs walk through leaves, but the pathfinder still files every leaf block as LEAVES,
 * which costs -1 (a wall). A mob that wanders or gets pushed into leaves then stands in a block its own pathfinder
 * calls solid: every path it asks for fails at the start, so it turns from one failed goal to the next and spins on
 * the spot. Here LEAVES costs a little extra instead (mobs still prefer to go round, like through water), for every
 * mob Traversable Leaves lets through and only while its config makes leaves traversable. A mob that set its own
 * leaf cost keeps it.
 */
@Mixin(Mob.class)
public abstract class LeafMalusMixin {
    private static final float PATCHWORK_LEAF_COST = 2.0F;

    @Inject(method = "getPathfindingMalus", at = @At("RETURN"), cancellable = true, require = 1)
    private void patchwork$leavesArePassable(PathType type, CallbackInfoReturnable<Float> cir) {
        if (type == PathType.LEAVES && cir.getReturnValueF() < 0.0F
                && ((ITraversableBlock) Blocks.OAK_LEAVES).isTraversable()
                && ITraversableEntity.canTraverse((Mob) (Object) this)) {
            cir.setReturnValue(PATCHWORK_LEAF_COST);
        }
    }
}
