package dev.chill.patchwork;

import com.mojang.logging.LogUtils;
import dev.chill.patchwork.campfireresting.CampfireSync;
import dev.chill.patchwork.nestedjars.NestedJarOffload;
import dev.chill.patchwork.villagerapi.VillagerPackCache;
import dev.chill.patchwork.voicelesssurvival.MobLeakFix;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/** Per-mod fixes for the Foundation pack; see {@link Module} for what each module does. */
@Mod(Patchwork.MOD_ID)
public class Patchwork {
    public static final String MOD_ID = "patchwork_fixes";
    public static final Logger LOG = LogUtils.getLogger();

    public Patchwork(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.STARTUP, PatchworkSpec.SPEC, "patchwork.toml");
        if (Module.oldPatchBoxInstalled()) {
            LOG.warn("[Patchwork] An old PatchBox jar is also installed; Patchwork stays off. Remove patchbox-*.jar from mods.");
        }
        MobLeakFix.register();
        if (Module.NESTED_JARS.active()) {
            // Discovery is over by now; a second pass after loading catches anything opened later.
            NestedJarOffload.startPass("construct");
            modBus.addListener(FMLLoadCompleteEvent.class, e -> NestedJarOffload.startPass("load complete"));
        }
        if (Module.CAMPFIRE_RESTING.active()) {
            // Checked here: CampfireSync names Campfire Resting types, so it must not even load without the mod.
            CampfireSync.register();
        }
        if (Module.VILLAGER_API.active()) {
            VillagerPackCache.register();
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PatchworkClient.init(modBus, container);
        }
    }
}
