package dev.chill.patchwork.mixin.punchynea;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Punchy Moves opens a "Conflicting animations" screen on world join whenever Not Enough Animations is installed.
 * Punchy Moves already handles NEA itself: its own NotEnoughAnimationsSuppressMixin cancels NEA's player transform for
 * every player Punchy Moves is posing, and NEA keeps animating everything else. So the two run together and the
 * warning only interrupts the join. Here the NEA check of that warning reads false; the Fresh Animations player
 * resource pack check, which Punchy Moves does not handle, still warns as before.
 */
@Pseudo
@Mixin(targets = "punchymoves.client.ui.ConflictingAnimationsWarning", remap = false)
public abstract class NeaWarningMixin {
    @Unique private static final Logger patchwork$LOG = LoggerFactory.getLogger("Patchwork");
    @Unique private static boolean patchwork$logged;

    @ModifyExpressionValue(method = "tick",
            at = @At(value = "FIELD", target = "Lpunchymoves/client/ui/ConflictingAnimationsWarning;NEA_INSTALLED:Z", opcode = org.objectweb.asm.Opcodes.GETSTATIC),
            remap = false, require = 1)
    private static boolean patchwork$neaHandled(boolean installed) {
        if (installed && !patchwork$logged) {
            patchwork$logged = true;
            patchwork$LOG.info("[Patchwork] punchynea: Not Enough Animations runs alongside Punchy Moves (Punchy Moves hands NEA the players it does not pose); conflict screen for NEA skipped");
        }
        return false;
    }
}
