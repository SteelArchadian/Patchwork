package dev.chill.patchwork.mixin.watut;

import com.corosus.watut.PlayerStatusManagerClient;
import dev.chill.patchwork.watut.WatutLivePreview;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Hooks WATUT's client tick and networking for {@link WatutLivePreview}: frees departed players' frame memory, decides
 * when a full (key) frame is needed, and takes over sending and decoding gui images.
 */
@Mixin(value = PlayerStatusManagerClient.class, remap = false)
public abstract class PlayerStatusManagerClientMixin {
    @Inject(method = "tickGame()V", at = @At("HEAD"), remap = false)
    private void patchwork$tickStart(CallbackInfo ci) {
        WatutLivePreview.onTickStart((PlayerStatusManagerClient) (Object) this);
    }

    //first call: the "render a new frame" path, whose result WATUT ignores
    @Redirect(method = "tickGame()V", at = @At(value = "INVOKE", target = "Lcom/corosus/watut/PlayerStatusManagerClient;updateNearbyPlayerListAndCheckIfNewPlayerNear()Z", ordinal = 0), remap = false)
    private boolean patchwork$nearbyOnCapture(PlayerStatusManagerClient self) {
        return WatutLivePreview.nearbyCheckOnCapture(self);
    }

    //second call: the "resend the last frame to someone who walked up" path
    @Redirect(method = "tickGame()V", at = @At(value = "INVOKE", target = "Lcom/corosus/watut/PlayerStatusManagerClient;updateNearbyPlayerListAndCheckIfNewPlayerNear()Z", ordinal = 1), remap = false)
    private boolean patchwork$nearbyOnResend(PlayerStatusManagerClient self) {
        return WatutLivePreview.nearbyCheckOnResend(self);
    }

    //Patchwork sends its own frames as they're encoded; WATUT's sender would resend a stale or delta-only image
    @Inject(method = "sendScreenRenderData(Lcom/corosus/watut/PlayerStatus;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void patchwork$noWatutSend(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "receiveAny(Ljava/util/UUID;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"), remap = false)
    private void patchwork$receive(UUID uuid, CompoundTag data, CallbackInfo ci) {
        WatutLivePreview.receive((PlayerStatusManagerClient) (Object) this, uuid, data);
    }
}
