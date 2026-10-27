package dev.chill.patchwork.mixin.soundwatchdog;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.chill.patchwork.soundwatchdog.SoundWatchdog;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/** See {@link SoundWatchdog}: a dead sound thread is restarted each tick, and play() waits at most a second for a channel. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow @Final private SoundEngineExecutor executor;

    @Inject(method = "tick(Z)V", at = @At("HEAD"), require = 1)
    private void patchwork$restartDeadThread(boolean isGamePaused, CallbackInfo ci) {
        SoundWatchdog.ensureAlive(executor);
    }

    @WrapOperation(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"), require = 1)
    private Object patchwork$boundedWait(CompletableFuture<?> future, Operation<Object> original) {
        return SoundWatchdog.await(future, executor);
    }
}
