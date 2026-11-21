package dev.chill.patchwork.mixin.c2meoclfallback;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Test hook only: with -Dpatchwork.test.c2meocl.nofp64=true every OpenCL device reports no cl_khr_fp64, so a PC with a
 * capable GPU takes the same path as a GPU without fp64 (laptop Intel UHD). Without the flag nothing changes.
 */
@Pseudo
@Mixin(targets = "com.ishland.c2me.opts.accel.opencl.common.enumeration.OpenCLDeviceLocator", remap = false)
public abstract class NoFp64TestMixin {
    @Unique private static final boolean patchwork$NO_FP64 = Boolean.getBoolean("patchwork.test.c2meocl.nofp64");

    @ModifyExpressionValue(method = "enumeratePlatformDevices",
            at = @At(value = "FIELD", target = "Lorg/lwjgl/opencl/CLCapabilities;cl_khr_fp64:Z"),
            remap = false, require = 0)
    private static boolean patchwork$noFp64(boolean original) {
        return original && !patchwork$NO_FP64;
    }
}
