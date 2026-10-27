package dev.chill.patchwork.acceleratedrendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Render-thread state for the Accelerated Rendering + shader pack fix. */
public final class ShaderTranslucentGuard {

    private static final MethodHandle SHADERS_IN_USE = lookupIris();
    private static boolean inLevel;
    private static boolean blocking;

    private ShaderTranslucentGuard() {}

    public static void enterLevel() {
        inLevel = true;
        blocking = shadersInUse();
    }

    public static void exitLevel() {
        inLevel = false;
        blocking = false;
    }

    public static boolean blockForcedTranslucent() {
        return inLevel && blocking;
    }

    private static boolean shadersInUse() {
        if (SHADERS_IN_USE == null) {
            return false;
        }
        try {
            return (boolean) SHADERS_IN_USE.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    /** IrisApi.getInstance().isShaderPackInUse(), looked up reflectively so Iris stays optional. */
    private static MethodHandle lookupIris() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).invoke();
            return lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance)
                    .asType(MethodType.methodType(boolean.class));
        } catch (Throwable t) {
            return null;
        }
    }
}
