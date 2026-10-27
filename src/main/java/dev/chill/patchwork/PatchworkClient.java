package dev.chill.patchwork;

import dev.chill.patchwork.eatinganimation.EatingAnimationClient;
import dev.chill.patchwork.subtleeffects.SubtleCache;
import dev.chill.patchwork.watut.WatutLivePreview;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only wiring, kept out of {@link Patchwork} so a dedicated server never loads client classes. */
final class PatchworkClient {
    private PatchworkClient() {}

    static void init(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        EatingAnimationClient.init(modBus);
        VersionNotice.register();
        // Gate here, not inside: loading WatutLivePreview links WATUT classes, which crashes without WATUT.
        if (Module.WATUT.active()) {
            WatutLivePreview.init(modBus);
        }
        if (Module.SUBTLE_EFFECTS.active()) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> SubtleCache.nextTick());
        }
    }
}
