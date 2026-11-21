package dev.chill.patchwork.mixin.dungeonsprompt;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Dungeons Perspective's first-join question ("Would you like to enable the top-down perspective...") was one
 * unwrapped line, so on large screens with a big GUI scale it ran off both edges. Its title and text now wrap to the
 * screen width, with a margin on each side.
 */
@Mixin(targets = "com.cleannrooster.dungeons_iso.ui.FirstTimeScreen", remap = false)
public abstract class FirstTimeWrapMixin {
    @Redirect(method = "build", require = 2,
            at = @At(value = "INVOKE", target = "Lio/wispforest/owo/ui/component/Components;label(Lnet/minecraft/network/chat/Component;)Lio/wispforest/owo/ui/component/LabelComponent;"))
    private LabelComponent patchwork$wrappedLabel(Component text) {
        int width = ((Screen) (Object) this).width;
        return Components.label(text).maxWidth(Math.max(120, width - 60));
    }
}
