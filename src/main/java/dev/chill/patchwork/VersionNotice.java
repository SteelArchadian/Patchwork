package dev.chill.patchwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Client side of {@link VersionCheck}: once the game has finished loading, a toast says which mods updated past
 * the version their Patchwork fix was written for (and that those fixes are paused). Shown once per launch.
 */
final class VersionNotice {
    private VersionNotice() {}

    private static boolean shown;

    static void register() {
        if (!VersionCheck.changed().isEmpty()) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        //wait for the loading overlay so the toast isn't hidden behind it
        if (shown || mc.getOverlay() != null || (mc.screen == null && mc.level == null)) {
            return;
        }
        shown = true;

        //players see this: keep it calm and plain, the technical detail is in the startup log (VersionCheck.describe)
        List<VersionCheck.Changed> changed = VersionCheck.changed();
        String mods = changed.stream().map(VersionCheck.Changed::displayName).collect(Collectors.joining(", "));
        Component title = Component.translatableWithFallback("patchwork.version_changed.title", "Patchwork");
        Component body = changed.size() == 1
                ? Component.translatableWithFallback("patchwork.version_changed.body.one",
                        "%s updated, so its fix is off for now. The game works normally.", mods)
                : Component.translatableWithFallback("patchwork.version_changed.body.many",
                        "%s updated, so their fixes are off for now. The game works normally.", mods);
        //multiline wraps to toast width (the plain toast grows sideways off screen), 10 seconds to read it
        mc.getToasts().addToast(SystemToast.multiline(mc, new SystemToast.SystemToastId(10000L), title, body));
        Patchwork.LOG.info("[Patchwork] Showed the 'fix is off' notice for: {}", mods);
    }
}
