package dev.chill.patchwork;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Modules whose target mod is installed, but at a version the module was not written for. Those modules switched
 * themselves off (see {@link Module#active()}), so the mod runs exactly as its author shipped it until someone checks
 * the fix against the new version and adds that version to {@link Module}. This only reports it, loudly, so a mod
 * update never silently drops a fix.
 */
public final class VersionCheck {
    private VersionCheck() {}

    public record Changed(Module module, String displayName, String found) {
        public String expected() {
            return String.join(", ", module.targetVersions.stream().sorted().toList());
        }
    }

    private static List<Changed> changed;

    /** Read once at launch, like {@link Module#active()}. Modules switched off in patchwork.toml are not reported. */
    public static List<Changed> changed() {
        if (changed == null) {
            List<Changed> out = new ArrayList<>();
            for (Module module : Module.values()) {
                if (module.targetModId == null || module.targetVersions.isEmpty() || !module.enabled()) {
                    continue;
                }
                String found = Module.modVersion(module.targetModId);
                if (found != null && !module.targetVersions.contains(found)) {
                    out.add(new Changed(module, displayName(module.targetModId), found));
                }
            }
            changed = List.copyOf(out);
        }
        return changed;
    }

    /** Status line for the startup log. */
    public static String describe(Module module) {
        if (module.active()) {
            return "active";
        }
        if (!module.enabled()) {
            return "off (patchwork.toml)";
        }
        if (Module.oldPatchBoxInstalled()) {
            return "off (an old PatchBox jar is also installed)";
        }
        if (module == Module.NESTED_JARS && Module.optimalActive("nestedjars")) {
            return "off (Optimal does this fix)";
        }
        for (Changed c : changed()) {
            if (c.module() == module) {
                return "SWITCHED OFF: " + c.displayName() + " is now " + c.found() + " but this fix was written for "
                        + c.expected() + ". The mod runs unpatched. Check the fix against " + c.found()
                        + ", then add that version to Module." + module.name() + ".";
            }
        }
        return "inactive (" + (module.targetModId != null ? module.targetModId : "target mod") + " not installed)";
    }

    private static String displayName(String modId) {
        for (ModInfo mod : LoadingModList.get().getMods()) {
            if (mod.getModId().equals(modId)) {
                return mod.getDisplayName();
            }
        }
        return modId;
    }
}
