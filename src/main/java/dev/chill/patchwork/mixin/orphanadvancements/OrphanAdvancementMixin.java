package dev.chill.patchwork.mixin.orphanadvancements;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An advancement whose parent does not exist is dropped with "Couldn't load advancements", and its rewards (often a
 * datapack function, e.g. Dungeons and Taverns' wandering trader map) never fire. Data packs do this when they name
 * "minecraft:root", which vanilla never had. An advancement with no display has no place on the advancement screen
 * anyway, so when its parent is missing it is loaded as its own hidden root instead: same criteria, same rewards.
 * Advancements that show on the screen, and every advancement whose parent exists, are left exactly as before.
 */
@Mixin(AdvancementTree.class)
public abstract class OrphanAdvancementMixin {
    @Unique private static final Logger patchwork$LOG = LoggerFactory.getLogger("Patchwork");

    @Shadow public abstract AdvancementNode get(ResourceLocation id);

    @ModifyVariable(method = "addAll", at = @At("HEAD"), argsOnly = true, require = 1)
    private Collection<AdvancementHolder> patchwork$rootOrphans(Collection<AdvancementHolder> holders) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (AdvancementHolder h : holders) ids.add(h.id());
        List<AdvancementHolder> out = null;
        int i = 0;
        for (AdvancementHolder h : holders) {
            Advancement a = h.value();
            Optional<ResourceLocation> parent = a.parent();
            if (parent.isPresent() && a.display().isEmpty() && !ids.contains(parent.get()) && get(parent.get()) == null) {
                if (out == null) {
                    out = new ArrayList<>(holders.size());
                    int j = 0;
                    for (AdvancementHolder p : holders) {
                        if (j++ == i) break;
                        out.add(p);
                    }
                }
                patchwork$LOG.info("[Patchwork] orphanadvancements: {} names missing parent {}; loaded as a hidden root (rewards kept)",
                        h.id(), parent.get());
                out.add(new AdvancementHolder(h.id(), new Advancement(Optional.empty(), a.display(), a.rewards(), a.criteria(),
                        a.requirements(), a.sendsTelemetryEvent(), a.name())));
            } else if (out != null) {
                out.add(h);
            }
            i++;
        }
        return out != null ? out : holders;
    }
}
