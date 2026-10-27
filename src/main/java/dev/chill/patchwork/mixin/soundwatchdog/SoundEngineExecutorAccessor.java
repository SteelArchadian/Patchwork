package dev.chill.patchwork.mixin.soundwatchdog;

import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SoundEngineExecutor.class)
public interface SoundEngineExecutorAccessor {
    @Accessor("thread")
    Thread patchwork$getThread();

    @Accessor("thread")
    void patchwork$setThread(Thread thread);

    @Accessor("shutdown")
    boolean patchwork$isShutdown();

    @Invoker("createThread")
    Thread patchwork$createThread();
}
