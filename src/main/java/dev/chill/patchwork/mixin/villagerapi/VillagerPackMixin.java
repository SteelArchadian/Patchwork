package dev.chill.patchwork.mixin.villagerapi;

import com.frikinjay.villagerapi.villagerpack.VillagerPackLoader;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.chill.patchwork.villagerapi.VillagerPackCache;
import org.spongepowered.asm.mixin.Mixin;

/** Callers close every pack after use; cached packs stay open until the server stops ({@link VillagerPackCache}). */
@Mixin(value = VillagerPackLoader.VillagerPack.class, remap = false)
public abstract class VillagerPackMixin {

    @WrapMethod(method = "close")
    private void patchwork$keepCachedOpen(Operation<Void> original) {
        if (!VillagerPackCache.isHeld(this)) {
            original.call();
        }
    }
}
