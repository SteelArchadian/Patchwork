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
            "Essential: while cosmetics are switched off (the switch at the top of the Wardrobe), its cosmetics layer is skipped in player rendering, so it costs nothing."),
    ESSENTIAL_UPDATES("essentialupdates", null, Set.of(),
            "Essential: never updates itself or nags about updates, in any profile (auto-update off, no update toast, flag or changelog window)."),
    ESSENTIAL_SALE("essentialsale", null, Set.of(),
            "Essential: no store sale banner on the title screen or wardrobe (Essential's own disableSale switch, applied reliably)."),
    ESSENTIAL_WARDROBE("essentialwardrobe", null, Set.of(),
            "Essential: one On/Off switch at the top of the Wardrobe for the whole Emotes and Cosmetics section. Off turns both off on this client and skips their rendering."),
    ESSENTIAL_ALWAYS_ON("essentialalwayson", null, Set.of(),
            "Essential: no \"Enable Essential\" switch in its settings and it is always on, so it can no longer be turned off by accident with no way back except editing its config."),
    EATING_ANIMATION("eatinganimation", null, Set.of(),
            "Eating Animation, ported to Java: same animations without Groovy ModLoader (~100 MB less RAM). "
                    + "Remove eatinganimation and gml from mods; does nothing while the original is installed."),
    SUBTLE_EFFECTS("subtleeffects", "subtle_effects", Set.of("1.15.0", "1.15.1", "1.15.2", "1.15.3"),
            "Subtle Effects: works out each animated block's biome, config conditions and block-list checks once per tick "
                    + "instead of up to five times. Same effects, less CPU."),
    WATUT("watut", "watut", Set.of("1.21.0-1.2.7"),
            "What Are They Up To: live, sharper in-world gui preview without the flicker; sends only changed pixels; crash-safe frame decoding."),
    ACCELERATED_RENDERING("acceleratedrendering", "acceleratedrendering", Set.of("1.0.14-1.21.1-alpha"),
            "Accelerated Rendering: with a shader pack on, player and piglin heads no longer show the sky through them. "
                    + "Only translucent models skip forced batching while shaders draw the world; the rest stays accelerated. Closing a second world renderer (portal views) no longer frees the buffers and shaders the game still draws with. Your own first-person body and arm (First-person Model, 3D Skin Layers) draw on the normal path, so the arm no longer stretches or turns flat and dark."),
    SUPPLEMENTARIES("supplementaries", "supplementaries", Set.of("1.21.1-3.9.9", "1.21.1-4.0.2", "1.21.1-4.1.1", "1.21.1-4.1.2", "1.21.1-4.1.3", "1.21.1-4.1.4"),
            "Supplementaries: the wrench no longer crashes the game on blocks without an item (like hanging cave vines) when Quark is installed."),
    CAMPFIRE_RESTING("campfireresting", "campfireresting", Set.of("1.6.0"),
            "Campfire Resting: player updates only go to players who can see that player, ending a flood of network errors in the log."),
    NOMADIC_TENTS("nomadictents", "nomadictents", Set.of("21.1.0"),
            "Nomadic Tents: right-clicking a tent door with a mallet packs the tent up instead of teleporting you inside."),
    STREAMS_REFLOWING("streamsreflowing", "streamsreflowing", Set.of("2.14.4"),
            "Streams Reflowing: turns on the mod's own switch that stops it writing chunk-stall thread dumps to disk. Rivers unchanged."),
    MIDNIGHTLIB("midnightlib", "midnightlib", Set.of("1.9.3", "1.10.0"),
            "MidnightLib: mods using it load their settings one at a time, fixing a random startup crash when two loaded at once."),
    IRIS("iris", "iris", Set.of("1.8.14-beta.1+mc1.21.1"),
            "Iris: shader-pack images that the pack never clears start out empty instead of with leftover video memory (fixes Bliss's glow)."),
    LOAD_MY_CHUNKS("loadmychunks", "loadmychunks", Set.of("1.2.5.1"),
            "Load My Chunks: walks a snapshot of the loaded chunks while ticking entities, fixing a world freeze and the game hanging on exit."),
    NESTED_JARS("nestedjars", "neoforge", Set.of("21.1.252", "21.1.265"),
            "NeoForge jar-in-jar: jars bundled inside other mods are read from a disk cache instead of being kept whole in memory "
                    + "(~100 MB less RAM in Foundation). Same files."),
    VILLAGER_API("villagerapi", "villagerapi", Set.of("1.26.9.6"),
            "Villager API: finds its villager packs once per world start instead of rescanning every mod for each step (about 1 s faster world loading), "
                    + "and stops reopening every mod and resource pack that has no villager pack (faster Resource Packs screen and F3+T)."),
    SEAMLESS_LOADING_SCREEN("seamlessloadingscreen", "seamless_loading_screen", Set.of("2.2.1+1.21"),
            "Seamless Loading Screen: worlds opened by other mods (auto-start, automation) get their screenshot name too, fixing a crash when leaving them."),
    NBT_STRINGS("nbtstrings", "minecraft", Set.of("1.21.1"),
            "Saved data: the key names and block names repeated through every loaded chunk (\"Name\", \"Palette\", \"minecraft:stone\") are read into one shared copy each instead of a new one every time. Less garbage while chunks load. Same data."),
    SIGN_TEXT("signtext", "minecraft", Set.of("1.21.1"),
            "Signs: text too far away to read (24 blocks; signTextDistance) is not drawn, the sign itself is. A spyglass still shows it."),
    RENDER_TYPE_NAMES("rendertypenames", "minecraft", Set.of("1.21.1"),
            "Render types: each one builds its name once instead of every time it is asked (Iris asks for every banner pattern, every frame). Looks the same."),
    IRIS_NAME_TAGS("irisnametags", "iris", Set.of("1.8.14-beta.1+mc1.21.1"),
            "Iris: with a shader pack, name tags no longer cast a floating shadow (and the shadow pass skips drawing them)."),
    SKIN_LAYERS("skinlayers3d", "skinlayers3d", Set.of("1.11.3", "1.11.4"),
            "3D Skin Layers: with a shader pack, the shadow uses the flat outer skin layer instead of the 3D one (about 10% more FPS; same outline)."),
    IRIS_SHADER_CACHE("irisshadercache", "iris", Set.of("1.8.14-beta.1+mc1.21.1"),
            "Iris: rewritten shader programs are kept on disk, so the first shader load after a launch skips the rewrite (about 1.4 s faster with Complementary). Same shaders."),
    STORMIES_SPIDERS("stormiespiders", "stormiespiders", Set.of("3.3.2"),
            "Stormie's Spiders: each spider scans the blocks around it once per tick instead of six times to pick the side it "
                    + "walks on (the biggest source of memory churn with many spiders). Same climbing."),
    AFK_CINEMA_HUD("afkcinema", "absoluteafkcinema", Set.of("1.3.0"),
            "Absolute AFK Cinema: the hotbar, HUD and camera view always come back when the cinema ends, not only the first time."),
    IMMERSIVE_PORTALS("immersiveportals", "immersive_portals", Set.of("6.0.6"),
            "Immersive Portals: keeps portal-linked dimensions loaded through a wrap instead of a redirect, so it no longer crashes alongside Load My Chunks (needs Patchwork's patched Immersive Portals jar)."),
    STUTTERFIX_EARLY("stutterfix", "stutterfix", Set.of("1.21.1-0.2.4"),
            "StutterFix: no crash at start when another mod (e.g. Immersive Portals through Connector) uses the background executor before mods finish loading."),
    OPENCL_STRUCTBLEND("openclstructblend", "c2me_opts_accel_opencl", Set.of("0.4.0-alpha.0.122+1.21.1", "0.4.0-alpha.0.124+1.21.1"),
            "C2ME OpenCL + StructBlend: chunk batches near StructBlend structures generate on C2ME's CPU path instead of failing to load (OpenCL can't take StructBlend's village underpinning or terrain beardifier)."),
    ACCESSORIES_MODEL_LEAK("accessoriesmodelleak", "accessories", Set.of("1.1.0-beta.54+1.21.1"),
            "Accessories: baked entity model parts no longer pile up in memory while playing (hundreds of MB over a "
                    + "long session). Parts no model takes are dropped at the end of each client tick."),
    MISSING_ASSETS("missingassets", null, Set.of(),
            "Missing models and textures: adds files that Bountiful (decree), Wandering Bags (hammer), Expanded Delight "
                    + "(cranberry cobbler with Farmer's Delight 1.3.4) and Spudacious Shops (plain shop items) reference but "
                    + "do not ship, so they no longer show purple-black. Each fix only loads for the exact mod version."),
    ORPHAN_ADVANCEMENTS("orphanadvancements", null, Set.of(),
            "Data packs: a hidden advancement whose parent does not exist (e.g. \"minecraft:root\") loads as its own hidden root "
                    + "instead of being dropped, so its rewards still fire (Dungeons and Taverns' wandering trader map). Visible "
                    + "advancements are unchanged."),
    FD_LOOT_TABLE_ALIAS("fdloottable", "farmersdelight", Set.of("1.3.4"),
            "Farmer's Delight: chest-loot modifiers from addons made for older Farmer's Delight (old \"lootTable\" key) load "
                    + "again instead of failing with \"No key table\", so their extra chest loot appears. Current files are unchanged."),
    C2ME_OCL_FALLBACK("c2meoclfallback", "c2me_opts_accel_opencl", Set.of("0.4.0-alpha.0.122+1.21.1", "0.4.0-alpha.0.124+1.21.1"),
            "C2ME OpenCL: on a PC where OpenCL can't start (no OpenCL driver or device, or a GPU without fp64 such as Intel UHD) "
                    + "worlds generate on C2ME's normal CPU path instead of the server crashing with \"No OpenCL devices found\". "
                    + "PCs where OpenCL works are unchanged."),
    C2ME_CHUNK_FUTURES("c2mechunkfutures", "c2me_rewrites_chunk_system", Set.of("0.4.0-alpha.0.122+1.21.1", "0.4.0-alpha.0.124+1.21.1"),
            "C2ME: a loaded chunk's ticking/full-chunk future is reused instead of rebuilt on every call (mods that check every chunk each tick made it ~9% of server allocation). Same answers."),
    JEI_COLORS("jeicolors", "jei", Set.of("19.51.0.418", "19.64.0.455"),
            "JEI: color search reuses one color histogram per thread instead of a new 128 KB one for every item texture. Same colors."),
    QUARK_POSTS("quarkposts", "quark", Set.of("4.1-487", "4.1-488"),
            "Quark: wood post shapes no longer copy the direction list on every shape query."),
    CLIENT_TWEAKS_SHAPES("clienttweaksshapes", "clienttweaks", Set.of("21.1.15"),
            "Client Tweaks: chain/pane building support skips blocks that aren't chains or panes before reading the held item, instead of on every block's shape query. Same shapes."),
    AMBIENT_SOUNDS_SCAN("ambientsoundsscan", "ambientsounds", Set.of("6.3.9", "6.3.11"),
            "AmbientSounds: the air pocket scan no longer copies the direction list on every step."),
    MODERNFIX_BIOMES("modernfixbiomes", "modernfix", Set.of("5.27.24+mc1.21.1", "5.27.27+mc1.21.1"),
            "ModernFix: the nearby-biome scan for surface rules reads each section's biome list instead of copying all 64 cells. Same terrain."),
    SDL_SOURCES("sdlsources", "sodiumdynamiclights", Set.of("1.0.9", "1.0.10"),
            "Sodium Dynamic Lights: keeps light sources in a list rebuilt on change, read without a lock or a new iterator per lit block, so adding or removing a light no longer waits for chunk meshing. Same light."),
    VB_LEAF_COLORS("vbleafcolors", "vanillabackport", Set.of("1.1.7.10", "1.2.2"),
            "Vanilla Backport: leaf litter colour no longer builds and throws away a tint cache for every block. Same colours."),
    VB_CONFIG_TICK("vbconfigtick", "vanillabackport", Set.of("1.2.2"),
            "Vanilla Backport: fixes a startup crash (\"Cannot get config value before config is loaded\") when its block-rendering watcher ticks before configs load; it waits until they are ready."),
    EARLY_CONFIG_TICK("earlyconfigtick", null, Set.of(),
            "Any mod: stops startup crashes (\"Cannot get config value before config is loaded\") from mods that read settings in client ticks before they have loaded. That tick is skipped for them until the settings are ready."),
    SR_SPAWN_WARM("srspawnwarm", "streamsreflowing", Set.of("2.14.4"),
            "Streams Reflowing: starts its river engine and spawn-area prefetch when the world opens instead of when spawn generation first waits on it. Same rivers, shorter spawn wait."),
    INVENTORY_HUD_CURIOS("inventoryhudcurios", "inventoryhud", Set.of("3.4.28"),
            "Inventory HUD: fixes a crash on joining a world when Curios slots have not arrived yet (endless retry loop)."),
    LITHIUM_NBT("lithiumnbt", "lithium", Set.of("0.15.4+mc1.21.1"),
            "Lithium: new NBT compound tags start with a small map instead of a 33-slot one (they nearly always hold a few keys). About a fifth of chunk save/load allocation. Same contents. Turning this off also leaves Lithium's own NBT map option off (vanilla maps)."),
    IRIS_PATH_CHAR("irispathchar", "iris", Set.of("1.8.14-beta.1+mc1.21.1"),
            "Iris: the check that allows capital letters in resource paths no longer makes a callback object for every character. Same paths allowed."),
    CINEMATIC_RESPAWN_BACK("cinematicrespawnback", "cinematic_respawn", Set.of("1.4.3", "1.5.0"),
            "Cinematic Respawn: screens opened from the death screen (e.g. Spawnstones' waystone picker) can go back to it again; Cancel no longer leaves you stuck."),
    FLYWHEEL_NOTIFY("flywheelnotify", "flywheel", Set.of("1.0.6"),
            "Flywheel (Create's renderer): handing a task to its worker threads wakes one worker without a lock, instead of waking all of them through a lock the render thread then waits on. Removes short render-thread hitches."),
    PUZZLES_HOLDER("puzzlesholder", "puzzleslib", Set.of("21.1.66"),
            "Puzzles Lib (all Fuzs mods): looking up a mod's settings no longer builds an error message every time (Easy Anvils did it for every block placed in world generation). Same settings."),
    C2ME_UNLOADED("c2meunloaded", "c2me_base", Set.of("0.4.0-alpha.0.124+1.21.1"),
            "C2ME: marking a chunk's later steps unloaded skips the ones already marked, instead of re-failing them (which only made garbage). Same chunk handling."),
    C2ME_GETCHUNK("c2megetchunk", "c2me_base", Set.of("0.4.0-alpha.0.122+1.21.1", "0.4.0-alpha.0.124+1.21.1"),
            "C2ME: the server's own lookups of a chunk it just used (the last 4) are answered from the chunk cache "
                    + "straight away, skipping C2ME's per-call tracking wrapper that made new garbage on every lookup. Same chunks."),
    C2ME_TICKING("c2meticking", "c2me_rewrites_chunk_system", Set.of("0.4.0-alpha.0.124+1.21.1"),
            "C2ME: asking whether a chunk is ticking no longer waits on that chunk's lock while C2ME works on it (mods ask every chunk every tick). Same answers, fewer server stalls."),
    FASTNBT_NAMES("fastnbtnames", "fastnbt", Set.of("1.21.1-26.9.1-neoforge"),
            "FastNBT: each block name in saved chunks is read once and reused, instead of parsed again for every palette entry. Less garbage while chunks load. Same blocks."),
    MODLIST_RECENT("modlistrecent", "neoforge", Set.of("21.1.252", "21.1.265"),
            "NeoForge mod list: a Recent sort button next to Off / A-Z / Z-A lists the mods you last clicked or configured first."),
    COLORWHEEL_ALERT("colorwheelalert", "colorwheel", Set.of("1.3.1+mc1.21.1", "1.3.2+mc1.21.1"),
            "Colorwheel: no red chat alert when the shader pack has no Colorwheel support. The alert that points to a ready-made Colorwheel version of the pack still shows."),
    DUNGEONS_PROMPT("dungeonsprompt", "dungeons_iso", Set.of("3.6.1"),
            "Dungeons Perspective: the first-join question wraps to the screen instead of running off both edges on large screens."),
    LEAF_PATHING("leafpathing", "traversable_leaves", Set.of("2.1.1"),
            "Traversable Leaves: mobs plan their paths through leaves the way they can walk through them, so a cow stuck in a leaf block walks out instead of spinning on the spot. Leaves cost a little extra, so mobs still go round when that is shorter."),
    EB_ALT_TAB("ebalttab", "effortlessbuilding", Set.of("4.2"),
            "Effortless Building: Alt+Tab no longer changes your build mode. The build wheel (held Left Alt) is cancelled when you press Tab or the game loses focus, so you keep the mode you had instead of whatever the mouse was over. Clicking a mode still works. The wheel also dims the world far less, so it no longer looks murky with shaders on."),
    SOUND_PHYSICS_PROXY("soundphysicsproxy", "sound_physics_remastered", Set.of("1.21.1-1.5.2"),
            "Sound Physics Remastered: sounds right after joining a world play without echo instead of throwing errors and being dropped while its world copy is still being made."),
    CARRYON_RENDER("carryonrender", "carryon", Set.of("2.2.6"),
            "Carry On: carried blocks and mobs are drawn with the world's other models, depth-tested, at their normal size, so they no longer show through walls or turn see-through with shader packs."),
    COLORWHEEL_FALLBACK("colorwheelfallback", "colorwheel", Set.of("1.3.1+mc1.21.1", "1.3.2+mc1.21.1"),
            "Colorwheel: shader packs without Colorwheel support draw Create/Flywheel models through the pack's own shaders (Colorwheel's fallback mode), so they get the right colours and shadows."),
    OBE_EMF("obeemf", "obe", Set.of("1.2.1-patch.1"),
            "Optimized Block Entities with Entity Model Features: chests, shulker boxes and other baked block entities that have an EMF custom model are drawn by their normal renderer (as EMF intends) instead of OBE's baked copy, which came out half a block off and sunk into the floor."),
    RELATIVE_BLOCKS("relativeblocks", "relativeblocks", Set.of("1.0.0", "2.0.0", "2.0.1", "2.0.2"),
            "Olaf's Relatively Placed Blocks: the mod as published loads on NeoForge (its mining-sound hook expects a call NeoForge changes, so Connector refused to start the game; Patchwork gives that call the shape the hook expects). Mining a cluster plays the sound of the small block under the crosshair, and combined blocks are drawn instead of invisible."),
    PARTICLE_RAIN_IDS("particlerainids", "particlerain", Set.of("4.0.2", "4.0.3", "4.0.4"),
            "Particle Rain: each particle style's sprite ids are parsed once and reused, instead of re-parsed for every rain, snow and dust particle. Same sprites, less garbage."),
    EB_WELCOME("ebwelcome", "effortlessbuilding", Set.of("4.2"),
            "Effortless Building: no welcome chat message when you join a world. Turn this off to get the message back."),
    CULL_RESET("cullreset", "entityculling", Set.of("1.11.3", "1.12.1"),
            "Entity Culling: before each culling pass, only the cache cells the last pass used are cleared, instead of the whole 4 MB cache. Same culling."),
    SOUND_WATCHDOG("soundwatchdog", "minecraft", Set.of("1.21.1"),
            "Minecraft sound engine: if the sound thread dies or stops answering, sounds are skipped and the thread restarted instead of the game freezing."),
    FABRIC_ATTRIBUTES("fabricattributes", "fabric_object_builder_api_v1", Set.of("15.2.1+cc242efd19", "15.2.1"),
            "Fabric API (Forgified, for Connector): Fabric mods that give their mobs attributes while the game is still starting (Better Nether, Better End) no longer crash it when a NeoForge mod hooks a vanilla mob (Atmospheric's camels). Those registrations wait for NeoForge's own attribute step. Same attributes."),
    ATLAS_BIOMES("atlasbiomes", "antiqueatlas", Set.of("8.0.1"),
            "Antique Atlas: biomes it has no tag for (cave biomes, many modded biomes) are drawn with the closest texture for their climate instead of left blank with an error in the log, and savanna biomes get their savanna texture (it asked for a misspelt name)."),
    LOOT_MISSING("lootmissing", "minecraft", Set.of("1.21.1"),
            "Minecraft loot tables: a table that names an item from a mod that is not installed no longer fails as a whole (empty chests in Integrated Dungeons and Structures, nothing from Spawn's anthills); only that item is left out."),
    BCLIB_LOOT("bclibloot", "bclib", Set.of("21.0.13"),
            "BCLib: extra loot BetterX mods add to existing tables (Better Nether's blue and weeping obsidian piglin barters) is added again; BCLib looked for the pool list in the wrong place and logged \"Unable to building bartering table\"."),
    CLIP_NAN("clipnan", "minecraft", Set.of("1.21.1"),
            "Block rays: a ray whose start or end is not a real position (infinite, NaN or past the world border) is skipped instead of looping forever; Touch n Motion's sign check froze the game this way."),
    STONEHOLM_POOLS("stoneholmpools", "stoneholm", Set.of("2.1.2+1.21.1"),
            "Stoneholm: with More Villagers installed, the world no longer crashes on start (\"primaryPoolAccessor is null\"). Stoneholm 2.1.2 tried to merge job-site pools that its jar no longer ships; that merge is skipped. Same villages."),
    BETTEREND_CAVES("betterendcaves", "betterend", Set.of("21.0.11"),
            "Better End: fallback for End caves. If the cave biome picker is still empty when the End loads (caves "
                    + "become end_highlands, carved bare, \"is not an EndCaveBiome\" in the log), it is rebuilt from the "
                    + "server's registries. The main fix is woverregistrypath."),
    WOVER_REGISTRY_PATH("woverregistrypath", "wover_core", Set.of("21.0.13"),
            "WorldWeaver (Better End, Better Nether through Connector): its datapack registries (biome data, surface "
                    + "rules, biome changes, preset info) load their files again. Four were missing from NeoForge's list "
                    + "and NeoForge looked under an extra namespace folder, so they loaded empty: End caves got "
                    + "end_highlands, carved bare, with \"is not an EndCaveBiome\" in the log."),
    STONEHOLM_BUDGET("stoneholmbudget", "stoneholm", Set.of("2.1.2+1.21.1"),
            "Stoneholm: a very large underground village can no longer keep a world-generation thread busy for minutes "
                    + "(and hang the game on exit). Each village stops growing at a piece and time budget (config: "
                    + "stoneholmMaxPieces, stoneholmMaxMillis); smaller villages are unchanged."),
    PUNCHY_NEA("punchynea", "punchy_moves", Set.of("1.0.8", "1.0.9"),
            "Punchy Moves: no \"Conflicting animations\" screen for Not Enough Animations on every world join. Punchy Moves already hands NEA the players it does not animate, so both keep working; the Fresh Animations pack warning stays."),
    SPATIAL_ESSENTIAL("spatialessential", "spatial_gui", Set.of("1.11.1-hotfix"),
            "Spatial GUI + Essential: Essential's menus read the window size many times a frame and Spatial GUI walked the "
                    + "call stack on each read to see who asked. Reads from Essential are now recognised without the walk; same result, "
                    + "less render-thread time on menus."),
    EMF_DEFER("emfdefer", "entity_model_features", Set.of("3.3.12"),
            "Entity Model Features: sets up model animations just after the loading screen instead of during it (about 1.3 s less "
                    + "frozen Mojang logo). Every animation is ready before any mob is drawn; same animations."),
    SLABBED_CACHE("slabbedcache", "slabbed", Set.of("0.6.13-alpha+1.21.1-neoforge"),
            "Slabbed: how far a block sits lowered is worked out once per tick per block instead of on every collision check "
                    + "(climbing spiders and every mob's ground check asked about the same blocks many times). Answers are dropped "
                    + "on any block change. Same lowered blocks, much less server tick time with many mobs."),
    SLABBED_LEAVES("slabbedleaves", "slabbed", Set.of("0.6.13-alpha+1.21.1-neoforge"),
            "Slabbed + Traversable Leaves: both change how blocks collide at the same spot, so the game crashed on the first "
                    + "tick in a world. Patchwork does both jobs in one place: walk-through leaves and lowered slab blocks both work."),
    MATVB_CREATIVE("matvbcreative", "matinventory", Set.of("0.2.1"),
            "Inventory Expanded: the 3D mat shows in the creative Survival Inventory tab again (Vanilla Backport), and "
                    + "mount screens (llama, horse) keep their normal layout with buttons along the screen edge."),
    MAT_CREATIVE_TABS("matcreativetabs", "matinventory", Set.of("0.2.1"),
            "Inventory Expanded: after the Survival Inventory tab, other creative tabs are no longer stuck lifted with the "
                    + "search box and side buttons out of place. The normal layout comes back when the 3D mat goes away."),
    MAT_MOUNT_PANEL("matmountpanel", "matinventory", Set.of("0.2.1"),
            "Inventory Expanded + Better Horses: llama and horse screens show the normal top panel and a full-size 3D mat "
                    + "again (hidden spare slots below the screen no longer stretch the panel)."),
    MAT_PACK_ART("matpackart", "matinventory", Set.of("0.2.1"),
            "Inventory Expanded + GUI resource packs (Immersive Interfaces and similar): pack art above the 3D mat no longer "
                    + "gets a stretched band or a stray strip of the pack's hotbar art as its edge. Plain panels unchanged."),
    MAT_SPATIAL("matspatial", "matinventory", Set.of("0.2.1"),
            "Inventory Expanded + Spatial GUI: the survival inventory and the 3D mat under crafting, chest and other screens "
                    + "show on Spatial GUI's in-world panel again instead of an empty panel. Without Spatial GUI nothing changes.");

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
            case ESSENTIAL, ESSENTIAL_UPDATES, ESSENTIAL_SALE, ESSENTIAL_WARDROBE, ESSENTIAL_ALWAYS_ON -> true;
            // Generic guard on NeoForge itself, not on one mod.
            case EARLY_CONFIG_TICK, ORPHAN_ADVANCEMENTS, MISSING_ASSETS -> true;
            // A replacement for the original: never run alongside it.
            case EATING_ANIMATION -> modVersion("eatinganimation") == null;
            // Optimal ships the same fix; when its copy is on, it does the work.
            case OPENCL_STRUCTBLEND -> {
                // Set.of(...).contains(null) throws: the OpenCL add-on is often not installed.
                String version = modVersion(targetModId);
                yield version != null && targetVersions.contains(version) && modVersion("structblend") != null;
            }
            // Needs both mods at known versions, and MixinSquared to cancel their two clashing hooks.
            case SLABBED_LEAVES -> {
                String version = modVersion(targetModId);
                yield version != null && targetVersions.contains(version) && "2.1.1".equals(modVersion("traversable_leaves"))
                        && dev.chill.patchwork.slabbedleaves.SlabbedLeaves.register(Module.class.getClassLoader());
            }
            case NESTED_JARS -> {
                String version = modVersion(targetModId);
                yield version != null && targetVersions.contains(version) && !optimalActive("nestedjars");
            }
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
