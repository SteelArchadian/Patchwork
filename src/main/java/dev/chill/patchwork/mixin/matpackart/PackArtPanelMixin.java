package dev.chill.patchwork.mixin.matpackart;

import dev.chill.patchwork.matpackart.PanelArt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Inventory Expanded cuts a container screen's background where its 3D mat starts, then fills the gap by stretching
 * one texture row and borrows the texture's bottom 7 rows as the panel's lower edge. With resource packs that paint
 * art instead of a plain panel (Immersive Interfaces and similar), that stretched row turned into a big band and the
 * "edge" was a strip of the pack's own hotbar art; the side columns showed pieces of the pack's backpack picture.
 * When the stretched row is not a plain panel colour, the art is drawn as-is (true rows, full width) from the top down
 * to where the mat starts, and the stretch, side columns and borrowed edge are skipped. Plain panels (vanilla, recolour
 * packs) and the merchant layout are untouched. Works for any pack and any screen, nothing hard-coded.
 */
@Pseudo
@Mixin(targets = "com.example.matinventory.screen.NativePanelFooter", remap = false)
public abstract class PackArtPanelMixin {
    @Shadow private static boolean drawing;
    @Shadow private static int merchantBottom;

    @Unique private static java.lang.reflect.Field patchwork$active;
    @Unique private static Method patchwork$left, patchwork$top, patchwork$width, patchwork$height, patchwork$cut, patchwork$blit;
    @Unique private static boolean patchwork$broken;

    @Inject(method = "crop", at = @At("HEAD"), cancellable = true, require = 0)
    private static void patchwork$packArt(GuiGraphics g, ResourceLocation texture, int x0, int x1, int y0, int y1, int z,
                                          float u0, float u1, float v0, float v1, CallbackInfoReturnable<Boolean> cir) {
        if (drawing || merchantBottom > 0 || y1 <= y0 || patchwork$broken) return;
        try {
            if (patchwork$active == null) patchwork$init();
            Object c = patchwork$active.get(null);
            if (c == null || x0 != (int) patchwork$left.invoke(c) || y0 != (int) patchwork$top.invoke(c)
                    || x1 - x0 != (int) patchwork$width.invoke(c) || y1 - y0 != (int) patchwork$height.invoke(c)) return;
            int cut = (int) patchwork$cut.invoke(c);
            if (cut <= 0 || cut >= y1 - y0) return;
            float dv = (v1 - v0) / (float) (y1 - y0);
            if (!PanelArt.isArt(texture, u0, u1, v1 - 5.5f * dv)) return;
            drawing = true;
            try {
                patchwork$blit.invoke(g, texture, x0, x1, y0, y0 + cut, z, u0, u1, v0, v0 + cut * dv);
            } finally {
                drawing = false;
            }
            cir.setReturnValue(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            patchwork$broken = true;
            org.slf4j.LoggerFactory.getLogger("Patchwork").warn("[Patchwork] matpackart off: {}", e.toString());
        }
    }

    @Unique
    private static void patchwork$init() throws ReflectiveOperationException {
        Class<?> owner = Class.forName("com.example.matinventory.screen.NativePanelFooter");
        java.lang.reflect.Field f = owner.getDeclaredField("active");
        f.setAccessible(true);
        Class<?> crop = f.getType();
        patchwork$left = patchwork$m(crop, "left");
        patchwork$top = patchwork$m(crop, "top");
        patchwork$width = patchwork$m(crop, "width");
        patchwork$height = patchwork$m(crop, "height");
        patchwork$cut = patchwork$m(crop, "cut");
        patchwork$blit = GuiGraphics.class.getMethod("matinventory$blit", ResourceLocation.class, int.class, int.class,
                int.class, int.class, int.class, float.class, float.class, float.class, float.class);
        patchwork$active = f;
    }

    @Unique
    private static Method patchwork$m(Class<?> c, String name) throws NoSuchMethodException {
        Method m = c.getDeclaredMethod(name);
        m.setAccessible(true);
        return m;
    }
}
