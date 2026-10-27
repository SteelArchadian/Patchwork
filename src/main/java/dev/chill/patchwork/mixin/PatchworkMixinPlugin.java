package dev.chill.patchwork.mixin;

import com.mojang.logging.LogUtils;
import dev.chill.patchwork.Module;
import dev.chill.patchwork.VersionCheck;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Applies a module's mixins only when that module is active (toggle on, target mod at a supported version). */
public class PatchworkMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LogUtils.getLogger();
    private final Set<Module> reported = EnumSet.noneOf(Module.class);

    public void onLoad(String mixinPackage) {
        // Runs before mod classes load, so Streams Reflowing's ChunkStallWatch reads this in its static initializer.
        // Its own switch, so no mixin; a value the player set with -D wins.
        if (Module.STREAMS_REFLOWING.active() && System.getProperty("streamsreflowing.nostalldump") == null) {
            System.setProperty("streamsreflowing.nostalldump", "true");
            LOG.info("[Patchwork] streamsreflowing: set -Dstreamsreflowing.nostalldump=true (no stall dump files)");
        }
        for (Module module : Module.values()) {
            boolean versionChanged = VersionCheck.changed().stream().anyMatch(c -> c.module() == module);
            if (versionChanged) {
                LOG.warn("[Patchwork] {}: {}", module.key, VersionCheck.describe(module));
            } else {
                LOG.info("[Patchwork] {}: {}", module.key, VersionCheck.describe(module));
            }
        }
    }

    public String getRefMapperConfig() { return null; }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Module module = Module.forMixin(mixinClassName);
        boolean apply = module != null && module.active();
        if (module != null && apply && reported.add(module)) {
            LOG.debug("[Patchwork] applying {} mixins", module.key);
        }
        return apply;
    }

    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
