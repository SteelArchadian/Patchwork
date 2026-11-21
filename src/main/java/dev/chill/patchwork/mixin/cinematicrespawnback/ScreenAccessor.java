package dev.chill.patchwork.mixin.cinematicrespawnback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface ScreenAccessor {
    /** Set when a screen is first shown, so a non-null value means the screen was already on screen once. */
    @Accessor("minecraft")
    Minecraft patchwork$minecraft();
}
