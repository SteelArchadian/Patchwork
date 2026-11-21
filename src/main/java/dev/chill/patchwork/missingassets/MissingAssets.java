package dev.chill.patchwork.missingassets;

import dev.chill.patchwork.Module;
import dev.chill.patchwork.Patchwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Small built-in resource packs that add model or texture files other mods reference but do not ship (purple-black
 * items and blocks). One pack per mod, under resourcepacks/missingassets/<modid>, added only while that mod (and any
 * other mod the fix depends on) is installed at the exact version the fix was written for. The packs only add files
 * that are missing, so they change nothing else. They are always on and hidden from the resource pack screen, since
 * there is nothing to choose.
 */
public final class MissingAssets {
    /** Pack folder (target mod id) -> required mods and their exact versions. */
    private static final Map<String, Map<String, String>> PACKS = Map.of(
            // Decree item model names texture bountiful:item/decree; the jar ships decree-unknown etc. only.
            "bountiful", Map.of("bountiful", "8.0.0-beta.2"),
            // Custom hammer model's Blockbench parent "hammer_from_farlands" resolves to minecraft:, which does not exist.
            "wandering_bags", Map.of("wandering_bags", "1.1"),
            // Cranberry cobbler models use farmersdelight:block/pie*, which Farmer's Delight 1.3.4 renamed to template_pie*.
            "expandeddelight", Map.of("expandeddelight", "0.1.4", "farmersdelight", "1.3.4"),
            // Blockstates name item models shop_<wood>; only the cushion-colour variants ship.
            "spudaciousshops", Map.of("spudaciousshops", "1.10.3-neoforge-1.21.1"));

    private MissingAssets() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AddPackFindersEvent.class, MissingAssets::onAddPackFinders);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        PACKS.forEach((pack, needs) -> {
            for (Map.Entry<String, String> need : needs.entrySet()) {
                if (!need.getValue().equals(Module.modVersion(need.getKey()))) return;
            }
            String id = "mod/" + Patchwork.MOD_ID + ":resourcepacks/missingassets/" + pack;
            Path root = ModList.get().getModFileById(Patchwork.MOD_ID).getFile().findResource("resourcepacks/missingassets/" + pack);
            String version = ModList.get().getModFileById(Patchwork.MOD_ID).versionString();
            Pack created = Pack.readMetaAndCreate(
                    new PackLocationInfo(id, Component.literal("Patchwork: " + pack + " missing assets"), PackSource.BUILT_IN,
                            Optional.of(new KnownPack("neoforge", id, version))),
                    BuiltInPackSource.fromName(path -> new PathPackResources(path, root)),
                    PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (created == null) return;
            Pack hidden = created.hidden();
            event.addRepositorySource(consumer -> consumer.accept(hidden));
            Patchwork.LOG.info("[Patchwork] missingassets: added missing {} models/textures", pack);
        });
    }
}
