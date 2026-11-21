package dev.chill.patchwork.mixin.inventoryhudcurios;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashMap;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Inventory HUD 3.4.28: setupTrinkets() calls CuriosSaveUtils.sync(), which reads the slot map back through
 * slots(). When Curios has not synced its slot types yet the map is empty and `last` is still old, so slots()
 * calls setupTrinkets() again, endlessly (StackOverflowError just after joining a world). An empty map has
 * nothing to load or save, so the sync waits for the next try (slots() retries every 3 s).
 */
@Pseudo
@Mixin(targets = "dlovin.inventoryhud.gui.renderers.CuriosRenderer", remap = false)
public abstract class CuriosSetupRecursionMixin {
    @Shadow(remap = false)
    private HashMap<String, ?> slots;

    @Unique
    private static boolean patchwork$logged;

    @WrapOperation(method = "setupTrinkets", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Ldlovin/inventoryhud/utils/CuriosSaveUtils;sync()V", remap = false))
    private void patchwork$skipEmptySync(Operation<Void> original) {
        if (slots == null || slots.isEmpty()) {
            if (!patchwork$logged) {
                patchwork$logged = true;
                LoggerFactory.getLogger("Patchwork").info("[Patchwork/inventoryhudcurios] Curios slots not synced yet; Inventory HUD will retry");
            }
            return;
        }
        original.call();
    }
}
