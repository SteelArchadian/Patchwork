package dev.chill.patchwork;

import dev.chill.patchwork.devtest.AllBlocksTest;
import dev.chill.patchwork.devtest.ARCloseTest;
import dev.chill.patchwork.devtest.CarryTest;
import dev.chill.patchwork.devtest.DeathCancelTest;
import dev.chill.patchwork.devtest.ClusterRenderTest;
import dev.chill.patchwork.devtest.EbAltTabTest;
import dev.chill.patchwork.devtest.LeafPathTest;
import dev.chill.patchwork.devtest.EssentialScreenTest;
import dev.chill.patchwork.essential.WardrobeSwitch;
import dev.chill.patchwork.devtest.FullscreenTest;
import dev.chill.patchwork.devtest.ModScreenTest;
import dev.chill.patchwork.devtest.ModelDumpTest;
import dev.chill.patchwork.devtest.ShotTest;
import dev.chill.patchwork.eatinganimation.EatingAnimationClient;
import dev.chill.patchwork.emfdefer.EmfDefer;
import dev.chill.patchwork.modscreens.ModSettingsInjector;
import dev.chill.patchwork.subtleeffects.SubtleCache;
import dev.chill.patchwork.watut.WatutLivePreview;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only wiring, kept out of {@link Patchwork} so a dedicated server never loads client classes. */
final class PatchworkClient {
    private PatchworkClient() {}

    static void init(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        EatingAnimationClient.init(modBus);
        VersionNotice.register();
        ModSettingsInjector.init(modBus);
        if (Module.MISSING_ASSETS.active()) {
            dev.chill.patchwork.missingassets.MissingAssets.init(modBus);
        }
        // Gate here, not inside: loading WatutLivePreview links WATUT classes, which crashes without WATUT.
        if (Module.WATUT.active()) {
            WatutLivePreview.init(modBus);
        }
        if (Module.ACCESSORIES_MODEL_LEAK.active()) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                    e -> dev.chill.patchwork.accessoriesmodelleak.ModelQueueClear.tick());
        }
        if (Module.SUBTLE_EFFECTS.active()) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> SubtleCache.nextTick());
        }
        if (DeathCancelTest.ENABLED) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> DeathCancelTest.tick());
        }
        if (AllBlocksTest.ON) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> AllBlocksTest.tick());
        }
        if (CarryTest.BLOCKS != null) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> CarryTest.tick());
        }
        if (ModelDumpTest.ON) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ModelDumpTest.tick());
        }
        if (ShotTest.LABEL != null) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ShotTest.tick());
        }
        if (Module.ESSENTIAL.active() || Module.ESSENTIAL_WARDROBE.active() || Module.ESSENTIAL_ALWAYS_ON.active()) {
            WardrobeSwitch.init(Module.ESSENTIAL_WARDROBE.active(), Module.ESSENTIAL_ALWAYS_ON.active());
        }
        if (EssentialScreenTest.ON) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> EssentialScreenTest.tick());
        }
        if (ARCloseTest.ON) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ARCloseTest.tick());
        }
        if (ClusterRenderTest.ON) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ClusterRenderTest.tick());
        }
        if (EbAltTabTest.ON) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> EbAltTabTest.tick());
        }
        if (LeafPathTest.ON) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> LeafPathTest.tick());
        }
        if (FullscreenTest.ON) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> FullscreenTest.tick());
        }
        if (ModScreenTest.MOD != null) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ModScreenTest.tick());
        }
        if (Module.EMF_DEFER.active()) {
            NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, e -> EmfDefer.frame());
        }
    }
}
