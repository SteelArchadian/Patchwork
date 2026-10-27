package dev.chill.patchwork.mixin.irisshadercache;

import dev.chill.patchwork.irisshadercache.ShaderTransformCache;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Iris 1.8.14-beta.1 keeps transformed shader programs only in an in-memory cache, so every launch re-parses and
 * re-patches each program of the shader pack (about 1.3 s for Complementary). This puts a disk cache behind the same
 * call; see {@link ShaderTransformCache} for the key. A miss runs Iris's own transform unchanged.
 */
@Mixin(value = TransformPatcher.class, remap = false)
public abstract class TransformPatcherMixin {

    @Shadow
    private static Map<PatchShaderType, String> transformInternal(String name, Map<PatchShaderType, String> inputs, Parameters p) {
        throw new AssertionError();
    }

    @Redirect(method = {"transform", "transformCompute"}, at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;transformInternal(Ljava/lang/String;Ljava/util/Map;Lnet/irisshaders/iris/pipeline/transform/parameter/Parameters;)Ljava/util/Map;"))
    private static Map<PatchShaderType, String> patchwork$diskCache(String name, Map<PatchShaderType, String> inputs, Parameters p) {
        return ShaderTransformCache.transform(name, inputs, p, () -> transformInternal(name, inputs, p));
    }
}
