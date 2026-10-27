package dev.chill.patchwork.iris;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Whether Iris is drawing its shadow pass right now; always false without Iris. */
public final class ShadowPass {

    private static final MethodHandle IN_SHADOW_PASS = lookupIris();

    private ShadowPass() {}

    public static boolean active() {
        if (IN_SHADOW_PASS == null) {
            return false;
        }
        try {
            return (boolean) IN_SHADOW_PASS.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    /** IrisApi.getInstance().isRenderingShadowPass(), looked up reflectively so Iris stays optional. */
    private static MethodHandle lookupIris() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).invoke();
            return lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(instance)
                    .asType(MethodType.methodType(boolean.class));
        } catch (Throwable t) {
            return null;
        }
    }
}
