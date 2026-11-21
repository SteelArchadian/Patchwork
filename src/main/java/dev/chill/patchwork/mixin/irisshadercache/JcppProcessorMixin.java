package dev.chill.patchwork.mixin.irisshadercache;

import com.llamalad7.mixinextras.sugar.Local;
import dev.chill.patchwork.irisshadercache.JcppCache;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.GlslCollectingListener;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris 1.8.14-beta.1 runs JCPP over every program of the shader pack on each pack load. A hit returns the stored
 * output at the head; a miss runs Iris's own preprocessing unchanged and stores the result at its return.
 * See {@link JcppCache} for the key.
 */
@Mixin(value = JcppProcessor.class, remap = false)
public abstract class JcppProcessorMixin {
    @Inject(method = "glslPreprocessSource", at = @At("HEAD"), cancellable = true)
    private static void patchwork$lookup(String source, Iterable<StringPair> defines, CallbackInfoReturnable<String> cir) {
        String cached = JcppCache.lookup(source, defines);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(method = "glslPreprocessSource", at = @At(value = "RETURN", ordinal = 0))
    private static void patchwork$store(String source, Iterable<StringPair> defines, CallbackInfoReturnable<String> cir,
                                        @Local GlslCollectingListener listener) {
        JcppCache.store(cir.getReturnValue(), listener);
    }
}
