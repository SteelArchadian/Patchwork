package dev.chill.patchwork.mixin.watut;

import com.corosus.watut.WatutNetworking;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The server keeps a per-player snapshot of WATUT packets to replay to players who log in. Gui image chunks were merged
 * into it too, so the server held the last chunk (up to 31 KB) per player and replayed a useless fragment of an image.
 */
@Mixin(targets = "com.corosus.watut.PlayerStatusManagerServer", remap = false)
public abstract class PlayerStatusManagerServerMixin {
    @Redirect(method = "receiveAny(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;merge(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"), remap = false)
    private CompoundTag patchwork$skipImageInCache(CompoundTag cache, CompoundTag data) {
        if (data.contains(WatutNetworking.NBTDataPlayerScreenCompressedPixelData)) {
            return cache;
        }
        return cache.merge(data);
    }
}
