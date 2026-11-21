package dev.chill.patchwork.mixin.emfdefer;

import dev.chill.patchwork.emfdefer.EmfDefer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An entity drawn on a menu (inventory, title screen) gets every queued EMF animation set up first. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void patchwork$drain(CallbackInfo ci) {
        EmfDefer.drainAll();
    }
}
