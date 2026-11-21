package dev.chill.patchwork.mixin.slabbedcache;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.util.SlabSupport;
import dev.chill.patchwork.slabbedcache.OffsetCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Keeps Slabbed's per-block lowered offset for the rest of the tick (see OffsetCache). */
@Mixin(value = SlabSupport.class, remap = false)
public abstract class YOffsetCacheMixin {
    @Shadow @Final private static ThreadLocal<Boolean> IN_GET_Y_OFFSET;
    @Shadow @Final private static ThreadLocal<Boolean> IN_RAW_SHAPE_PROBE;
    @Shadow @Final private static ThreadLocal<Boolean> IN_CEILING_CAP_PROBE;
    @Shadow @Final private static ThreadLocal<Boolean> VANILLA_COLLISION_SHAPE_QUERY;
    @Shadow @Final private static ThreadLocal<Object> SUPPORT_RESOLUTION_CONTEXT;

    @WrapMethod(method = "getYOffsetGuarded")
    private static double patchwork$cachedYOffset(BlockGetter world, BlockPos pos, BlockState state, boolean consultStoredHeight,
                                                  Operation<Double> original) {
        boolean idle = !Boolean.TRUE.equals(IN_GET_Y_OFFSET.get()) && !Boolean.TRUE.equals(IN_RAW_SHAPE_PROBE.get())
                && !Boolean.TRUE.equals(IN_CEILING_CAP_PROBE.get()) && !Boolean.TRUE.equals(VANILLA_COLLISION_SHAPE_QUERY.get());
        return OffsetCache.offset(world, pos, state, consultStoredHeight, idle, idle ? SUPPORT_RESOLUTION_CONTEXT.get() : null, original);
    }
}
