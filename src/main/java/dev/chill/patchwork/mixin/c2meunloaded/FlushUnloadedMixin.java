package dev.chill.patchwork.mixin.c2meunloaded;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * C2ME's scheduler marks a chunk's higher statuses unloaded by collecting their futures into a list and failing each
 * one. Most of those slots already hold the shared, already-failed UNLOADED_FUTURE, and failing a future that is
 * already done still allocates its result wrapper before finding out it is too late: about 26 MB/s in a Test Dummy
 * capture. Already-unloaded slots are not collected again, and done futures are not failed again (which did nothing).
 */
@Pseudo
@Mixin(targets = "com.ishland.flowsched.scheduler.ItemHolder", remap = false)
public abstract class FlushUnloadedMixin {
    @Shadow(remap = false) @Final private static CompletableFuture<Void> UNLOADED_FUTURE;

    @WrapOperation(method = "flushUnloadedStatus", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z", remap = false))
    private boolean patchwork$skipUnloaded(ArrayList<Object> list, Object future, Operation<Boolean> original) {
        return future != UNLOADED_FUTURE && original.call(list, future);
    }

    @WrapOperation(method = "flushUnloadedStatus", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;completeExceptionally(Ljava/lang/Throwable;)Z", remap = false))
    private boolean patchwork$skipDone(CompletableFuture<?> future, Throwable ex, Operation<Boolean> original) {
        return !future.isDone() && original.call(future, ex);
    }
}
