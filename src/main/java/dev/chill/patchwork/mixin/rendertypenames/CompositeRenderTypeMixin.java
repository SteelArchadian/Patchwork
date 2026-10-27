package dev.chill.patchwork.mixin.rendertypenames;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Iris's banner batching tags every pattern layer, every frame, with its RenderType's toString(), which rebuilds a
 * long string from all of the type's state shards (Shaders profile: ~20% of banner rendering). A render type never
 * changes after it is built, so the string is built once and kept.
 */
@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public abstract class CompositeRenderTypeMixin {
    @Unique
    private String patchwork$name;

    @WrapMethod(method = "toString")
    private String patchwork$cachedName(Operation<String> original) {
        String name = patchwork$name;
        if (name == null) {
            name = original.call();
            patchwork$name = name;
        }
        return name;
    }
}
