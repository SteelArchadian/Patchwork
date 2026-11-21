# Patchwork

Fixes for other people's mods, in one small mod for NeoForge 1.21.1. Each fix is a separate module that:

- is **on by default** and switches itself on only when its target mod is installed at the exact version it was
  written for;
- **switches itself off** if that mod updates, and tells you once ("<Mod> updated, so its fix is off for now. The game
  works normally.");
- can be turned off in **Mods → Patchwork → Config** (or `config/patchwork.toml`).

Removing any of the target mods is safe: its module just stays off.

## Modules

| Target mod | Version | What it fixes |
|---|---|---|
| Biolith (bundled in Quark) | 3.0.14 | Allocation-free biome lookups during world generation (~163 → ~7 MB/s) |
| TerraBlender | 4.1.0.8 | No throwaway lambda and second lookup per surface block (~166 MB/s less garbage) |
| Terrain Slabs | 3.1.2 | Slab scan reads each block once (~200 MB/s less garbage) |
| Voiceless Survival | 2.0.1 | Memory leak that kept every mob in memory for the whole session |
| Essential | any | Skips the cosmetics render layer (~61 MB/s render-thread garbage) |
| Essential | any | No update popups on the menus (a required update still shows; manual update still works) |
| Eating Animation | replaces it | Java port (MIT), so Groovy ModLoader isn't needed (~107 MB RAM) |
| Subtle Effects | 1.15.0 | Per-tick biome/condition work done once instead of up to five times |
| Accelerated Rendering | 1.0.14-1.21.1-alpha | See-through heads with shader packs |
| What Are They Up To | 1.21.0-1.2.7 | Live, sharper GUI preview; delta frames; crash-safe decoding (every player needs Patchwork) |
| Supplementaries | 1.21.1-3.9.9 | Wrench crash on itemless blocks with Quark |
| Campfire Resting | 1.6.0 | Network error flood from syncing players the client can't see |
| Nomadic Tents | 21.1.0 | Mallet on a tent door teleports you in instead of packing up |
| Streams Reflowing | 2.14.4 | Turns on the mod's own switch to stop chunk-stall thread dumps |
| MidnightLib | 1.9.3 | Random startup crash when two mods using it load their settings at once |
| Iris | 1.8.14-beta.1+mc1.21.1 | Shader images the pack never clears start empty instead of with leftover video memory (Bliss glow) |
| Load My Chunks | 1.2.5.1 | Endless loop while ticking entities (world freeze, game hangs on exit) |
| NeoForge (jar-in-jar) | 21.1.252 | Jars bundled inside mods are read from a disk cache instead of being held whole in memory (~65-117 MB) |
| Villager API | 1.26.9.6 | Finds its villager packs once per world start instead of rescanning every mod for each step (~1 s faster world loading) |
| 3D Skin Layers | 1.11.3 | With a shader pack, the 3D outer skin layer is drawn flat in the shadow pass (~10% more FPS; shadows keep their outline) |
| Iris | 1.8.14-beta.1 | With a shader pack, name tags no longer cast a floating shadow; the first shader load after launch is faster (rewritten shaders are cached on disk) |
| Minecraft (sound engine) | 1.21.1 | If the sound thread crashes or stops answering, sounds are skipped and the thread restarted instead of the game freezing |
| Minecraft (signs) | 1.21.1 | Sign text too far away to read (24 blocks, adjustable) isn't drawn; big FPS gain around many signs |
| Minecraft (render types) | 1.21.1 | Render type names are built once (with shaders, banners asked for them every frame); ~7.5% more FPS near banners |
| Seamless Loading Screen | 2.2.1+1.21 | No crash when leaving a world that was opened by another mod (auto-start, automation) |

The performance modules don't change results. Each has a verify mode (`-Dpatchbox.verify.<key>=true`) that runs the
original code side by side and logs any difference, and all were checked on a ~290-mod pack with 0 differences.

## Building

1. Put the compile-only jars listed in `build.gradle` (`dependencies { compileOnly files('libs/...') }`) into `libs/`.
   They're the mods' normal releases from CurseForge/Modrinth and aren't included in this repository.
2. `./gradlew build` → `build/libs/patchbox-1.21.1-1.0.0.jar`.

Each module lives in `src/main/java/dev/chill/patchbox/mixin/<key>/` (plus helpers in `dev.chill.patchbox.<key>`) and is registered in `Module.java`.

## License

Copyright (c) 2026 Chill. Licensed under the GNU LGPL v3.0 only (`LICENSE`, with the GPL text in `COPYING`). Third-party parts and their notices: `NOTICE.md`.
