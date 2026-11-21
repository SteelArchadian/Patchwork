package dev.chill.patchwork.mixin.essentialsale;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.slf4j.LoggerFactory;

/**
 * Hides Essential's store sale banner ("SPOOKY SALE 3d 4h left") on the title screen and the wardrobe. Essential has
 * its own off switch, the essential.disableSale system property, but reads it once when SaleNoticeManager is built,
 * which can be before another mod sets it (EssentialTweaks sets it in its mod constructor and its self-check still
 * says ok). The read is answered "true" here, so Essential builds an empty sale list itself. Its other buttons,
 * notices and the store are untouched.
 */
@Pseudo
@Mixin(targets = "gg.essential.network.connectionmanager.notices.SaleNoticeManager", remap = false)
public abstract class SaleNoticeMixin {
    @ModifyExpressionValue(method = "<init>", require = 1, remap = false, at = @At(value = "INVOKE",
            target = "Ljava/lang/System;getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            remap = false))
    private String patchwork$disableSale(String value) {
        if (!"true".equals(value)) {
            LoggerFactory.getLogger("Patchwork").info("[Patchwork/essentialsale] Essential sale banner off");
        }
        return "true";
    }
}
