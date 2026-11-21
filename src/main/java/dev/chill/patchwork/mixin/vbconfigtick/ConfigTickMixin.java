package dev.chill.patchwork.mixin.vbconfigtick;

import com.blackgear.platform.core.util.config.ConfigBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Vanilla Backport's block-rendering watcher runs on every client tick from the first frame and reads its config
 * values every 40 ticks. If a client tick lands before NeoForge has loaded those configs (slow or busy startup),
 * the read throws "Cannot get config value before config is loaded" and the game crashes while loading.
 * This waits until every watched value can be read, then lets the original check run unchanged.
 */
@Pseudo
@Mixin(targets = "com.blackgear.vanillabackport.client.api.modules.block_rendering.DynamicBlockRenderingHandler", remap = false)
public abstract class ConfigTickMixin {
    @Shadow(remap = false) @Final public static List<ConfigBuilder.ConfigValue<?>> WATCHED_CONFIG_VALUES;
    @Shadow(remap = false) public int ticksUntilNextCheck;

    private static boolean patchwork$ready;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void patchwork$waitForConfig(CallbackInfo ci) {
        if (patchwork$ready || ticksUntilNextCheck > 0) {
            return;
        }
        try {
            for (ConfigBuilder.ConfigValue<?> value : WATCHED_CONFIG_VALUES) {
                value.get();
            }
            patchwork$ready = true;
        } catch (IllegalStateException notLoadedYet) {
            ticksUntilNextCheck = 40;
            ci.cancel();
        }
    }
}
