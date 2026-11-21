package dev.chill.patchwork.mixin.flywheelnotify;

import dev.chill.patchwork.flywheelnotify.TaskSignal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/** Gives each Flywheel notifier its own {@link TaskSignal}. */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.task.ThreadGroupNotifier", remap = false)
public abstract class ThreadGroupNotifierMixin implements TaskSignal.Holder {
    @Unique
    private final TaskSignal patchwork$signal = new TaskSignal();

    @Override
    public TaskSignal patchwork$signal() {
        return patchwork$signal;
    }
}
