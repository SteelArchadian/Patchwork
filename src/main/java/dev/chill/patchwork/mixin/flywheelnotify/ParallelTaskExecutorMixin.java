package dev.chill.patchwork.mixin.flywheelnotify;

import dev.chill.patchwork.flywheelnotify.TaskSignal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Flywheel (inside Create) posts every task with a synchronized notifyAll: each post wakes every sleeping worker, they
 * all retake the same lock, and the render thread queues behind them on its next post (40 waits, worst 45 ms, in a
 * Test Dummy capture). Posting now wakes one worker without a lock; stopping still wakes them all.
 */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.task.ParallelTaskExecutor", remap = false)
public abstract class ParallelTaskExecutorMixin {
    @Redirect(method = "execute", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/impl/task/ThreadGroupNotifier;postNotification()V", remap = false))
    private void patchwork$postOne(@Coerce Object notifier) {
        ((TaskSignal.Holder) notifier).patchwork$signal().post();
    }

    @Redirect(method = "stopWorkers", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Ljava/lang/Object;notifyAll()V", remap = false))
    private void patchwork$wakeAll(Object notifier) {
        ((TaskSignal.Holder) notifier).patchwork$signal().wakeAll();
        notifier.notifyAll();
    }
}
