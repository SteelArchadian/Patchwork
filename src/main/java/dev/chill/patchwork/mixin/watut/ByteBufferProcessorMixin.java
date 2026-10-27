package dev.chill.patchwork.mixin.watut;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WATUT's frame encoder thread is no longer used (Patchwork encodes on its own daemon thread). Its executor creates the
 * thread lazily, so not submitting the polling loop means the non-daemon thread is never started at all.
 */
@Mixin(targets = "com.corosus.watut.client.screen.ByteBufferProcessor", remap = false)
public abstract class ByteBufferProcessorMixin {
    @Inject(method = "startProcessing()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void patchwork$noThread(CallbackInfo ci) {
        ci.cancel();
    }
}
