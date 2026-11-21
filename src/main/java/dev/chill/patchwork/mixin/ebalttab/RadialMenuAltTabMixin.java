package dev.chill.patchwork.mixin.ebalttab;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Effortless Building opens its build-mode wheel while Left Alt is held and, on release, switches to whatever the
 * mouse is over. Alt+Tab holds that same Alt, so tabbing out picked a random mode. Now the wheel is cancelled (your
 * mode stays as it was) when Tab is pressed while it is open or the game window loses focus before Alt comes up.
 * Clicking a mode with the mouse still works as before. The wheel also dims the world much less (it was blacked out
 * to about 60%, which with shaders on looked murky), so you can still see what you are building.
 */
@Mixin(targets = "neoforge.nl.requios.effortlessbuilding.screen.RadialMenu", remap = false)
public abstract class RadialMenuAltTabMixin extends Screen {
    @Unique
    private boolean patchwork$cancelled;
    @Unique
    private boolean patchwork$focused;

    protected RadialMenuAltTabMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), require = 1, remap = false)
    private void patchwork$reset(CallbackInfo ci) {
        patchwork$cancelled = false;
        patchwork$focused = Minecraft.getInstance().isWindowActive();
    }

    @Unique
    private boolean patchwork$lostFocus() {
        return patchwork$focused && !Minecraft.getInstance().isWindowActive();
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 1, remap = false)
    private void patchwork$watchAltTab(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // Alt+Tab is handled by Windows, so the game often never sees the Tab: losing focus is the sure sign.
        if (patchwork$lostFocus() || InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_TAB)) {
            patchwork$cancelled = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            patchwork$cancelled = true;
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Inject(method = "performAction", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void patchwork$keepModeOnAltTab(boolean fromMouseClick, CallbackInfo ci) {
        if (!fromMouseClick && (patchwork$cancelled || patchwork$lostFocus())) {
            ci.cancel();
        }
    }

    /** The wheel's screen dim: alpha 150 of 255 at full fade-in, now 50. */
    @ModifyConstant(method = "render", constant = @Constant(floatValue = 150.0F), require = 0, remap = false)
    private float patchwork$lighterDim(float alpha) {
        return 50.0F;
    }
}
