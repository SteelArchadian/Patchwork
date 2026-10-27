package dev.chill.patchwork.villagerapi;

import dev.chill.patchwork.Patchwork;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Villager API 1.26.9.6 rescans every mod root and reopens every villager pack each time
 * {@code VillagerPackLoader.getDiscoveredPacks()} is called, and a world start calls it several times (biome mappings,
 * trades, village pools): about a second of file reading per start. While a server runs, this keeps the first result
 * per packs folder and hands each caller its own copy of the list. Callers close the packs when they are done, so
 * {@link #isHeld} makes that close a no-op for cached packs; they are really closed when the server stops.
 * <p>
 * Names no Villager API types, so it is safe to load without the mod (the packs are only {@link AutoCloseable} here).
 */
public final class VillagerPackCache {
    private static final Map<Path, List<AutoCloseable>> BY_DIR = new HashMap<>();
    private static final Set<Object> HELD = Collections.newSetFromMap(new IdentityHashMap<>());

    private VillagerPackCache() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> release());
    }

    @SuppressWarnings("unchecked")
    public static <T extends AutoCloseable> List<T> get(Path dir, Supplier<List<T>> discover) {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return discover.get(); // no world running (e.g. the title screen): nothing would clear the cache
        }
        synchronized (HELD) {
            List<AutoCloseable> packs = BY_DIR.get(dir);
            if (packs == null) {
                packs = List.copyOf(discover.get());
                BY_DIR.put(dir, packs);
                HELD.addAll(packs);
            }
            return new ArrayList<>((List<T>) packs);
        }
    }

    public static boolean isHeld(Object pack) {
        synchronized (HELD) {
            return HELD.contains(pack);
        }
    }

    private static void release() {
        List<AutoCloseable> packs = new ArrayList<>();
        synchronized (HELD) {
            BY_DIR.values().forEach(packs::addAll);
            BY_DIR.clear();
            HELD.clear();
        }
        for (AutoCloseable pack : packs) {
            try {
                pack.close();
            } catch (Exception e) {
                Patchwork.LOG.debug("[Patchwork] villagerapi: closing a cached pack failed", e);
            }
        }
    }
}
