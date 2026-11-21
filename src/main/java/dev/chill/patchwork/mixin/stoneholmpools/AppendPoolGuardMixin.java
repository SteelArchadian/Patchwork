package dev.chill.patchwork.mixin.stoneholmpools;

import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stoneholm 2.1.2 still merges More Villagers' job-site pools into its own point_of_interest pools when More Villagers
 * is installed, but the jar no longer ships either set of pools. The registry hands back null and appendPool crashes
 * the world on start ("primaryPoolAccessor is null"). A merge with a missing pool is now skipped and logged once;
 * there is nothing to merge, so villages generate exactly as they would without More Villagers.
 */
@Pseudo
@Mixin(targets = "net.thegrimsey.stoneholm.util.StructurePoolUtils", remap = false)
public abstract class AppendPoolGuardMixin {
    @Unique private static final Logger patchwork$LOG = LoggerFactory.getLogger("Patchwork");
    @Unique private static boolean patchwork$warned;

    @Inject(method = "appendPool", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void patchwork$skipMissing(StructureTemplatePool target, StructureTemplatePool source, CallbackInfo ci) {
        if (target != null && source != null) return;
        if (!patchwork$warned) {
            patchwork$warned = true;
            patchwork$LOG.warn("[Patchwork] stoneholmpools: Stoneholm tried to merge a structure pool that does not exist ({} is missing); merge skipped instead of crashing",
                    target == null ? "its own pool" : "the add-on pool");
        }
        ci.cancel();
    }
}
