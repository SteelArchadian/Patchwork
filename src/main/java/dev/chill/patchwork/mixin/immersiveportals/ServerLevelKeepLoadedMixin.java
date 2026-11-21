package dev.chill.patchwork.mixin.immersiveportals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * Immersive Portals 6.0.6 and Load My Chunks 1.2.5.1 both @Redirect the same players.isEmpty() call in ServerLevel.tick
 * (the "no players for 300 ticks, stop ticking this dimension" check), each to keep their own dimensions loaded. Two
 * redirects can't share a call, so whichever applies second fails and the game crashes at start. Patchwork's patched
 * Immersive Portals jar drops its redirect; this wrap puts its rule back on top of whatever else handles the call
 * (Load My Chunks' redirect, or vanilla): a dimension Immersive Portals needs for a portal never counts as empty.
 */
@Mixin(value = ServerLevel.class, priority = 1100)
public abstract class ServerLevelKeepLoadedMixin {
    @Unique
    private static final MethodHandle patchwork$SHOULD_LOAD = patchwork$find();

    @Unique
    private static MethodHandle patchwork$find() {
        try {
            Class<?> tracking = Class.forName("qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking");
            return MethodHandles.publicLookup().findStatic(tracking, "shouldLoadDimension",
                    MethodType.methodType(boolean.class, ResourceKey.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Immersive Portals' ImmPtlChunkTracking.shouldLoadDimension not found", e);
        }
    }

    @WrapOperation(method = "tick", require = 1, at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean patchwork$keepPortalDimension(List<?> players, Operation<Boolean> original) {
        try {
            if ((boolean) patchwork$SHOULD_LOAD.invokeExact((ResourceKey<Level>) ((ServerLevel) (Object) this).dimension())) {
                return false;
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        return original.call(players);
    }
}
