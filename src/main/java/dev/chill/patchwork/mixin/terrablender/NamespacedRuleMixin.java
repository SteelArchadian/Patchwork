// SPDX-License-Identifier: LGPL-3.0-or-later
// Adapted from LGPL-3.0 code; see NOTICE.md. Patchwork as a whole is LGPL-3.0-only.
package dev.chill.patchwork.mixin.terrablender;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TerraBlender 4.1.0.8's NamespacedRule.tryApply runs for every surface block and allocated a capturing lambda
 * ({@code key -> rules.containsKey(namespace)}) for {@code Holder.is(Predicate)}, then looked the namespace up a
 * second time. For a registry-backed biome ({@link Holder.Reference}) {@code is(predicate)} is just
 * {@code predicate.test(key())}, so this reads the key directly and does one lookup; any other holder kind takes the
 * original path. The chosen rule is the same in every case.
 *
 * With {@code -Dpatchwork.verify.terrablender=true} each call also evaluates the original selection and logs any difference.
 */
@Mixin(targets = "terrablender.worldgen.surface.NamespacedSurfaceRuleSource$NamespacedRule", remap = false)
public abstract class NamespacedRuleMixin {
    @Unique
    private static final Logger patchwork$LOG = LogUtils.getLogger();
    @Unique
    private static final boolean patchwork$VERIFY = Boolean.getBoolean("patchwork.verify.terrablender");
    @Unique
    private static final AtomicLong patchwork$checked = new AtomicLong();
    @Unique
    private static final AtomicLong patchwork$mismatches = new AtomicLong();

    @Shadow
    @Final
    private SurfaceRules.Context context;
    @Shadow
    @Final
    private SurfaceRules.SurfaceRule baseRule;
    @Shadow
    @Final
    private Map<String, SurfaceRules.SurfaceRule> rules;

    /**
     * @author Chill (Patchwork)
     * @reason Same rule selection without a lambda allocation and a second map lookup per surface block.
     */
    @Overwrite
    public BlockState tryApply(int x, int y, int z) {
        Holder<Biome> biome = this.context.biome.get();
        SurfaceRules.SurfaceRule rule;
        if (biome instanceof Holder.Reference<Biome> reference) {
            rule = this.rules.get(reference.key().location().getNamespace());
        } else {
            rule = patchwork$originalSelection(biome);
        }
        if (patchwork$VERIFY) {
            patchwork$verify(rule, patchwork$originalSelection(biome));
        }

        BlockState state = null;
        if (rule != null) {
            state = rule.tryApply(x, y, z);
        }
        if (state == null) {
            state = this.baseRule.tryApply(x, y, z);
        }
        return state;
    }

    /** TerraBlender's own selection, verbatim. */
    @Unique
    private SurfaceRules.SurfaceRule patchwork$originalSelection(Holder<Biome> biome) {
        if (biome.is(key -> this.rules.containsKey(key.location().getNamespace()))) {
            return this.rules.get(biome.unwrapKey().get().location().getNamespace());
        }
        return null;
    }

    @Unique
    private static void patchwork$verify(SurfaceRules.SurfaceRule fast, SurfaceRules.SurfaceRule original) {
        long checked = patchwork$checked.incrementAndGet();
        if (fast != original) {
            long n = patchwork$mismatches.incrementAndGet();
            if (n <= 20) {
                patchwork$LOG.warn("[Patchwork/terrablender] surface rule mismatch #{}: fast {} vs original {}", n, fast, original);
            }
        }
        if ((checked & ((1 << 24) - 1)) == 0) {
            patchwork$LOG.info("[Patchwork/terrablender] verify: {} surface lookups checked, {} mismatches", checked, patchwork$mismatches.get());
        }
    }
}
