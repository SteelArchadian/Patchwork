package dev.chill.patchwork.modlistrecent;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For the modlistrecent module: when each mod was last clicked or had its config opened in the mod list, kept in
 * config/patchwork_fixes-recent-mods.txt (one "modid=time" line each) so the Recent sort survives restarts.
 */
public final class RecentMods {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("patchwork_fixes-recent-mods.txt");
    private static Map<String, Long> used;

    private RecentMods() {}

    /** When the mod was last used, 0 if never. */
    public static long lastUsed(String modId) {
        return load().getOrDefault(modId, 0L);
    }

    public static void touch(String modId) {
        load().put(modId, System.currentTimeMillis());
        List<String> lines = new ArrayList<>();
        used.forEach((id, time) -> lines.add(id + "=" + time));
        try {
            Files.write(FILE, lines);
        } catch (IOException e) {
            LOG.warn("[Patchwork] modlistrecent: could not save {}", FILE, e);
        }
    }

    private static Map<String, Long> load() {
        if (used == null) {
            used = new HashMap<>();
            if (Files.exists(FILE)) {
                try {
                    for (String line : Files.readAllLines(FILE)) {
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            try {
                                used.put(line.substring(0, eq).trim(), Long.parseLong(line.substring(eq + 1).trim()));
                            } catch (NumberFormatException ignored) {
                                // a hand-edited line; skip it
                            }
                        }
                    }
                } catch (IOException e) {
                    LOG.warn("[Patchwork] modlistrecent: could not read {}", FILE, e);
                }
            }
        }
        return used;
    }
}
