package dev.chill.patchwork.mixin.matspatial;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.chill.patchwork.matspatial.SpatialCapture;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Inventory Expanded draws its 3D mat into its own offscreen buffer, then switches back to the game's main buffer and
 * draws the result onto the screen. GUI mods that capture the whole screen into a buffer of their own (Spatial GUI
 * shows menus on a panel in the world) block every switch to the main buffer while they capture. So the switch back
 * was ignored: the mat picture was drawn into itself and every slot, item and label after it landed in the mat buffer
 * instead of the captured screen. Survival inventory showed nothing, crafting lost its mat.
 * Here the capture flag is lowered only while the mat renders offscreen (so the mat's own buffer switches work), and
 * the buffer that was bound before (the capture buffer) is put back where Inventory Expanded returns to the main one.
 * Without a capturing GUI mod nothing changes.
 */
@Pseudo
@Mixin(targets = "com.example.matinventory.render.MatSceneRenderer", remap = false)
public abstract class MatSpatialMixin {
    @Unique private static boolean patchwork$logged;
    @Unique private static int patchwork$depth;
    @Unique private static boolean patchwork$capturing;
    @Unique private static int patchwork$fbo;
    @Unique private static final int[] patchwork$viewport = new int[4];

    @Inject(method = "prepare", at = @At("HEAD"), require = 1)
    private void patchwork$start(CallbackInfo ci) {
        if (patchwork$depth++ != 0) return;
        patchwork$capturing = false;
        Field f = SpatialCapture.flag();
        if (f == null) return;
        try {
            if (!f.getBoolean(null)) return;
            patchwork$fbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, patchwork$viewport);
            f.setBoolean(null, false);
            patchwork$capturing = true;
            SpatialCapture.lowered = true;
            if (!patchwork$logged) {
                patchwork$logged = true;
                org.slf4j.LoggerFactory.getLogger("Patchwork")
                        .info("[Patchwork] matspatial: mat rendered offscreen, then back into Spatial GUI's screen capture");
            }
        } catch (Throwable e) {
            patchwork$capturing = false;
        }
    }

    @WrapOperation(method = "prepare",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"),
            require = 1)
    private void patchwork$back(RenderTarget target, boolean viewport, Operation<Void> original) {
        original.call(target, viewport);
        if (patchwork$capturing && patchwork$depth == 1 && target == Minecraft.getInstance().getMainRenderTarget()) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, patchwork$fbo);
            GlStateManager._viewport(patchwork$viewport[0], patchwork$viewport[1], patchwork$viewport[2], patchwork$viewport[3]);
            patchwork$end();
        }
    }

    @Inject(method = "prepare", at = @At("RETURN"), require = 1)
    private void patchwork$finish(CallbackInfo ci) {
        if (patchwork$depth == 1) patchwork$end();
        if (patchwork$depth > 0) patchwork$depth--;
    }

    @Unique
    private static void patchwork$end() {
        if (!patchwork$capturing) return;
        patchwork$capturing = false;
        SpatialCapture.lowered = false;
        try {
            SpatialCapture.flag().setBoolean(null, true);
        } catch (Throwable ignored) {
        }
    }
}
