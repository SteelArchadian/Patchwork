package dev.chill.patchwork.mixin.flywheelnotify;

import dev.chill.patchwork.flywheelnotify.TaskSignal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Idle Flywheel workers sleep on the notifier's {@link TaskSignal} instead of its monitor. */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.task.ParallelTaskExecutor$WorkerThread", remap = false)
public abstract class WorkerThreadMixin {
    @Redirect(method = "spinThenWait", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/impl/task/ThreadGroupNotifier;awaitNotification()V", remap = false))
    private void patchwork$sleep(@Coerce Object notifier) {
        ((TaskSignal.Holder) notifier).patchwork$signal().await();
    }
}
