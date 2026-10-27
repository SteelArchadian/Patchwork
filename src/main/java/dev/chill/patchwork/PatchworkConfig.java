package dev.chill.patchwork;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/patchwork.toml}: one {@code key = true/false} line per module. Read directly from disk because mixins
 * are chosen long before NeoForge's config system loads. Missing keys are added with their default (on), and comments
 * describe each module; anything else in the file is left as the user wrote it.
 */
public final class PatchworkConfig {
    private static final Logger LOG = LogUtils.getLogger();
    private static PatchworkConfig instance;

    private final Map<Module, Boolean> toggles = new EnumMap<>(Module.class);

    private PatchworkConfig() {}

    public static synchronized PatchworkConfig get() {
        if (instance == null) {
            Path file = FMLPaths.CONFIGDIR.get().resolve("patchwork.toml");
            migrateFromPatchBox(file);
            instance = load(file);
        }
        return instance;
    }

    /** Patchwork was called PatchBox until 2026-10-24: carry an old config/patchbox.toml over once. */
    private static void migrateFromPatchBox(Path file) {
        Path old = file.resolveSibling("patchbox.toml");
        if (Files.exists(file) || !Files.exists(old)) {
            return;
        }
        try {
            Files.move(old, file);
            LOG.info("[Patchwork] Moved your PatchBox settings from {} to {}", old.getFileName(), file.getFileName());
        } catch (IOException e) {
            LOG.warn("[Patchwork] Could not move {} to {}; every module stays on", old, file, e);
        }
    }

    public boolean enabled(Module module) {
        return toggles.getOrDefault(module, true);
    }

    private static PatchworkConfig load(Path file) {
        PatchworkConfig config = new PatchworkConfig();
        List<String> lines = new ArrayList<>();
        try {
            if (Files.exists(file)) {
                lines.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOG.warn("[Patchwork] Could not read {}; every module stays on", file, e);
        }

        for (String raw : lines) {
            String line = raw.strip();
            int eq = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[") || eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            int comment = value.indexOf('#');
            if (comment >= 0) {
                value = value.substring(0, comment).strip();
            }
            for (Module module : Module.values()) {
                if (module.key.equals(key)) {
                    config.toggles.put(module, !value.equalsIgnoreCase("false"));
                }
            }
        }

        boolean changed = lines.isEmpty();
        if (lines.isEmpty()) {
            lines.add("# Patchwork: per-mod fixes for the Foundation pack. Each module only runs if the mod it fixes is installed");
            lines.add("# at a version it was written for; set a module to false to turn it off. Restart the game after changing this.");
        }
        for (Module module : Module.values()) {
            if (!config.toggles.containsKey(module)) {
                lines.add("");
                lines.add("# " + module.description);
                lines.add(module.key + " = true");
                config.toggles.put(module, true);
                changed = true;
            }
        }
        if (changed) {
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.warn("[Patchwork] Could not write {}", file, e);
            }
        }
        return config;
    }
}
