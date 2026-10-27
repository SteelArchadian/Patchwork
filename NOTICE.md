# Third-party code and assets in Patchwork

| Path | Origin | License |
|---|---|---|
| `src/main/java/dev/chill/patchwork/mixin/biolith/RTreeSearchMixin.java` | Adapted from Biolith 3.0.14 (TerraformersMC), `MixinSearchTree.biolith$searchTreeGet`; also contains a verbatim copy used only by the verify mode | LGPL-3.0-or-later |
| `src/main/java/dev/chill/patchwork/mixin/terrablender/NamespacedRuleMixin.java` | Adapted from TerraBlender 4.1.0.8 (Glitchfiend), `NamespacedSurfaceRuleSource.NamespacedRule.tryApply` | LGPL-3.0-or-later |
| `src/main/java/dev/chill/patchwork/mixin/terrainslabs/SlabFeatureMixin.java` | Logic adapted from Terrain Slabs 3.1.2 (countered), `SlabFeature.generateSlabs` | MIT, see `src/main/resources/LICENSE-TerrainSlabs.txt` |
| `src/main/resources/resourcepacks/eatinganimation/`, `src/main/resources/eatinganimation_compat/`, `dev.chill.patchwork.eatinganimation` | Eating Animation by Matyrobbrt / Theoness1 | MIT, see `src/main/resources/LICENSE-EatingAnimation.txt` |

Patchwork is Copyright (c) 2026 Chill and licensed under the GNU Lesser General Public License v3.0 only (`LICENSE`; the GPL text it builds on is in `COPYING`). The files above keep their notices: the two LGPL-derived files are LGPL-3.0-or-later, used here under LGPL-3.0, and the MIT-licensed parts stay available under MIT as well.

Patchwork doesn't include or redistribute any of the mods it patches. Mixins target their classes at runtime, and the
jars in `libs/` are only for compiling, are not in this repository, and are never shipped.
