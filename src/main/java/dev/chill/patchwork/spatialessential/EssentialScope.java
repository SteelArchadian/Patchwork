package dev.chill.patchwork.spatialessential;

/**
 * Render-thread depth counter: above zero while Essential's UResolution is inside its own Window.getGuiScaled* /
 * getGuiScale call. Only touched on the render thread.
 */
public final class EssentialScope {
    public static int depth;

    private EssentialScope() {
    }
}
