package dev.chill.patchwork;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * The in-game view of {@code config/patchwork.toml} (Mods → Patchwork → Config). Same file and keys that
 * {@link PatchworkConfig} reads at launch, so the menu, the file and what actually runs always agree. Every toggle
 * needs a game restart because modules are wired in while the game starts.
 */
public final class PatchworkSpec {
    public static final ModConfigSpec SPEC;
    public static final Map<Module, ModConfigSpec.BooleanValue> TOGGLES = new EnumMap<>(Module.class);
    /** signtext module: sign text farther than this (blocks) is not drawn. */
    public static final ModConfigSpec.IntValue SIGN_TEXT_DISTANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        // Top level, ahead of [modules]: PatchworkConfig appends new module keys at the end of the file.
        SIGN_TEXT_DISTANCE = builder.comment("Sign text farther away than this many blocks is not drawn (signtext module).")
                .translation("patchwork_fixes.configuration.signTextDistance")
                .defineInRange("signTextDistance", 24, 8, 256);
        builder.comment("Patchwork modules. Each one only runs if the mod it fixes is installed at a version it was written for.")
                .push("modules");
        for (Module module : Module.values()) {
            TOGGLES.put(module, builder.comment(module.description)
                    .translation("patchwork_fixes.configuration." + module.key)
                    .gameRestart()
                    .define(module.key, true));
        }
        builder.pop();
        SPEC = builder.build();
    }

    private PatchworkSpec() {}
}
