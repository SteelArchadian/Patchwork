package dev.chill.patchwork;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

import java.util.Set;

/**
 * One Patchwork module per mod it fixes. A module runs only when its toggle in {@code config/patchwork.toml} is on and
 * the mod it targets is installed at a version it was written against, so a mod update can never be overwritten by
 * an outdated copy of its code (the module just switches itself off until it is updated).
 */
public enum Module {
    BIOLITH("biolith", "biolith", Set.of("3.0.14"),
            "Biolith (bundled in Quark and others): allocation-free biome lookups during world generation. Same biomes."),
    TERRABLENDER("terrablender", "terrablender", Set.of("4.1.0.8", "4.1.0-8"),
            "TerraBlender: no throwaway function per surface block during world generation. Same terrain."),
    TERRAIN_SLABS("terrainslabs", "terrain_slabs", Set.of("3.1.2"),
            "Terrain Slabs: scans each column once instead of 3 reads and 3 allocations per block. Same slabs."),
    VOICELESS_SURVIVAL("voicelesssurvival", "ezvcsurvival", Set.of("2.0.1"),
            "Voiceless Survival: fixes a leak that kept every mob that ever spawned in memory for the whole session."),
    ESSENTIAL("essential", null, Set.of(),
            "Essential: removes the cosmetics layer from player rendering (friends, hosting and messaging stay)."),
    EATING_ANIMATION("eatinganimation", null, Set.of(),
            "Eating Animation, ported to Java: same animations without Groovy ModLoader (~100 MB less RAM). "
                    + "Remove eatinganimation and gml from mods; does nothing while the original is installed."),
    SUBTLE_EFFECTS("subtleeffects", "subtle_effects", Set.of("1.15.0"),
            "Subtle Effects: works out each animated block's biome, config conditions and block-list checks once per tick "
                    + "instead of up to five times. Same effects, less CPU."),
    WATUT("watut", "watut", Set.of("1.21.0-1.2.7"),
            "What Are They Up To: live, sharper in-world gui preview without the flicker; sends only changed pixels; crash-safe frame decoding."),
    ACCELERATED_RENDERING("acceleratedrendering", "acceleratedrendering", Set.of("1.0.14-1.21.1-alpha"),
            "Accelerated Rendering: with a shader pack on, player and piglin heads no longer show the sky through them. "
                    + "Only translucent models skip forced batching while shaders draw the world; the rest stays accelerated."),
    SUPPLEMENTARIES("supplementaries", "supplementaries", Set.of("1.21.1-3.9.9"),
            "Supplementaries: the wrench no longer crashes the game on blocks without an item (like hanging cave vines) when Quark is installed."),
    CAMPFIRE_RESTING("campfireresting", "campfireresting", Set.of("1.6.0"),
            "Campfire Resting: player updates only go to players who can see that player, ending a flood of network errors in the log."),
    NOMADIC_TENTS("nomadictents", "nomadictents", Set.of("21.1.0"),
            "Nomadic Tents: right-clicking a tent door with a mallet packs the tent up instead of teleporting you inside."),
    STREAMS_REFLOWING("streamsreflowing", "streamsreflowing", Set.of("2.14.4"),
            "Streams Reflowing: turns on the mod's own switch that stops it writing chunk-stall thread dumps to disk. Rivers unchanged."),
    MIDNIGHTLIB("midnightlib", "midnightlib", Set.of("1.9.3"),
            "MidnightLib: mods using it load their settings one at a time, fixing a random startup crash when two loaded at once."),
    IRIS("iris", "iris", Set.of("1.8.14-beta.1+mc1.21.1"),
            "Iris: shader-pack images that the pack never clears start out empty instead of with leftover video memory (fixes Bliss's glow)."),
    LOAD_MY_CHUNKS("loadmychunks", "loadmychunks", Set.of("1.2.5.1"),
            "Load My Chunks: walks a snapshot of the loaded chunks while ticking entities, fixing a world freeze and the game hanging on exit."),
    NESTED_JARS("nestedjars", "neoforge", Set.of("21.1.252"),
            "NeoForge jar-in-jar: jars bundled inside other mods are read from a disk cache instead of being kept whole in memory "
                    + "(~100 MB less RAM in Foundation). Same files."),
    VILLAGER_API("villagerapi", "villagerapi", Set.of("1.26.9.6"),
            "Villager API: finds its villager packs once per world start instead of rescanning every mod for each step (about 1 s faster world loading)."),
    SEAMLESS_LOADING_SCREEN("seamlessloadingscreen", "seamless_loading_screen", Set.of("2.2.1+1.21"),
            "Seamless Loading Screen: worlds opened by other mods (auto-start, automation) get their screenshot name too, fixing a crash when leaving them."),
    SIGN_TEXT("signtext", "minecraft", Set.of("1.21.1"),
            "Signs: text too far away to read (24 blocks; signTextDistance) is not drawn, the sign itself is. A spyglass still shows it."),
    RENDER_TYPE_NAMES("rendertypenames", "minecraft", Set.of("1.21.1"),
            "Render types: each one builds its name once instead of every time it is asked (Iris asks for every banner pattern, every frame). Looks the same."),
    IRIS_NAME_TAGS("irisnametags", "iris", Set.of("1.8.14-beta.1+mc1.21.1"),
            "Iris: with a shader pack, name tags no longer cast a floating shadow (and the shadow pass skips drawing them)."),
    SKIN_LAYERS("skinlayers3d", "skinlayers3d", Set.of("1.11.3"),
            "3D Skin Layers: with a shader pack, the shadow uses the flat outer skin layer instead of the 3D one (about 10% more FPS; same outline)."),
    IRIS_SHADER_CACHE("irisshadercache", "iris", Set.of("1.8.14-beta.1+mc1.21.1"),
            "Iris: rewritten shader programs are kept on disk, so the first shader load after a launch skips the rewrite (about 1.4 s faster with Complementary). Same shaders."),
    SOUND_WATCHDOG("soundwatchdog", "minecraft", Set.of("1.21.1"),
            "Minecraft sound engine: if the sound thread dies or stops answering, sounds are skipped and the thread restarted instead of the game freezing.");

    /** Key in patchwork.toml and the mixin sub-package name. */
    public final String key;
    /** Mod id this module patches, or null for modules that detect their target another way. */
    public final String targetModId;
    public final Set<String> targetVersions;
    public final String description;

    Module(String key, String targetModId, Set<String> targetVersions, String description) {
        this.key = key;
        this.targetModId = targetModId;
        this.targetVersions = targetVersions;
        this.description = description;
    }

    public boolean enabled() {
        return PatchworkConfig.get().enabled(this);
    }

    /** Toggle on and target present at a supported version. */
    public boolean active() {
        // An old PatchBox jar left next to Patchwork already applies these fixes; never apply them twice.
        if (!enabled() || oldPatchBoxInstalled()) {
            return false;
        }
        return switch (this) {
            // Essential is loaded by its own loader; its mixin is @Pseudo and simply does nothing without it.
            case ESSENTIAL -> true;
            // A replacement for the original: never run alongside it.
            case EATING_ANIMATION -> modVersion("eatinganimation") == null;
            // Optimal ships the same fix; when its copy is on, it does the work.
            case NESTED_JARS -> targetVersions.contains(modVersion(targetModId)) && !optimalActive("nestedjars");
            default -> {
                // Set.of(...).contains(null) throws, and the target mod may not be installed at all.
                String version = modVersion(targetModId);
                yield version != null && targetVersions.contains(version);
            }
        };
    }

    public static Module forMixin(String mixinClassName) {
        for (Module module : values()) {
            if (mixinClassName.contains(".mixin." + module.key + ".")) {
                return module;
            }
        }
        return null;
    }

    /** Optimal's own toggle for a fix both mods ship; any failure (Optimal absent, older API) counts as off. */
    public static boolean optimalActive(String featureKey) {
        if (modVersion("optimal") == null) {
            return false;
        }
        try {
            return (Boolean) Class.forName("dev.chill.optimal.api.Optimal").getMethod("isActive", String.class)
                    .invoke(null, featureKey);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Patchwork was called PatchBox (mod id patchbox) until 2026-10-24. */
    public static boolean oldPatchBoxInstalled() {
        return modVersion("patchbox") != null;
    }

    public static String modVersion(String modId) {
        for (ModInfo mod : LoadingModList.get().getMods()) {
            if (mod.getModId().equals(modId)) {
                return mod.getVersion().toString();
            }
        }
        return null;
    }
}
