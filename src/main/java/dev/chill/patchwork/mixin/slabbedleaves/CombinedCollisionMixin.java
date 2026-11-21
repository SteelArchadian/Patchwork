package dev.chill.patchwork.mixin.slabbedleaves;

import com.dreu.traversableleaves.interfaces.ITraversableBlock;
import com.dreu.traversableleaves.interfaces.ITraversableEntity;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * One redirect for the call Slabbed and Traversable Leaves both redirect (their own two are cancelled). Traversable
 * Leaves' rules (same as its 2.1.1 BlockCollisionsMixin) decide leaves an entity moves through; every other block gets
 * Slabbed's lowered-cell shape instead of the plain one.
 */
@Mixin(BlockCollisions.class)
public abstract class CombinedCollisionMixin {
    @Redirect(method = "computeNext", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape patchwork$slabbedLeavesShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (state.getBlock() instanceof ITraversableBlock leaves && leaves.isTraversable()
                && ctx instanceof EntityCollisionContext entityCtx && entityCtx.getEntity() != null) {
            Entity entity = entityCtx.getEntity();
            if (entity instanceof LivingEntity living) {
                if (living instanceof Player player && player.isCreative() && player.getAbilities().flying) {
                    return Shapes.empty();
                }
                VoxelShape shape = leaves.accessGetCollisionShapeTL(state, level, pos, ctx);
                if (ctx.isAbove(shape, pos, false) && !ctx.isDescending()) {
                    return leaves.accessGetCollisionShapeTL(state, level, pos, CollisionContext.empty());
                }
                if (ITraversableEntity.canTraverse(living)) {
                    return Shapes.empty();
                }
            } else if (!(entity instanceof ItemEntity)) {
                return Shapes.empty();
            }
        }
        return SlabSupport.collisionShapeForBroadphaseCell(state, level, pos, ctx);
    }
}
