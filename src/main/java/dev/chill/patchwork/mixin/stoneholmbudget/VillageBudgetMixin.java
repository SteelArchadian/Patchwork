package dev.chill.patchwork.mixin.stoneholmbudget;

import dev.chill.patchwork.PatchworkSpec;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Deque;
import java.util.List;

/**
 * Stoneholm lays out an underground village piece by piece and checks every new piece against one shape that holds
 * every piece placed so far. That shape gets more detailed with each piece, so each check costs more than the last;
 * a large village can keep a world-generation thread busy for minutes, and the server then waits for it on shutdown
 * (odyssey-test job 1457: one village worker stuck 1.4 min, exit killed). Each village now has a budget: once it has
 * the configured number of pieces, or has spent the configured time, no more pieces are added and the village is
 * finished as it stands. Villages under the budget generate exactly as before. While the server is stopping, villages
 * still being laid out finish at once so shutdown is not held up (job 1463: village layouts kept running 30 s past
 * "Stopping!"). Measured cost grows faster than piece count: ~200 pieces about 1 s, ~300 pieces about 5 s.
 */
@Pseudo
@Mixin(targets = "net.thegrimsey.stoneholm.structures.StoneholmGenerator$StoneholmStructurePoolGenerator", remap = false)
public abstract class VillageBudgetMixin {
    @Unique private static final Logger patchwork$LOG = LoggerFactory.getLogger("Patchwork");
    @Unique private static final boolean patchwork$LOG_ALL = Boolean.getBoolean("patchwork.test.stoneholm.log");

    @Shadow(remap = false) @Final List<?> children;
    @Shadow(remap = false) @Final Deque<?> structurePieces;

    @Unique private long patchwork$start;
    @Unique private boolean patchwork$stopped;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void patchwork$begin(CallbackInfo ci) {
        patchwork$start = System.nanoTime();
    }

    @Unique
    private boolean patchwork$overBudget() {
        if (patchwork$stopped) return true;
        long ms = (System.nanoTime() - patchwork$start) / 1_000_000L;
        int pieces = children.size();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        boolean stopping = server != null && !server.isRunning();
        if (!stopping && pieces < PatchworkSpec.STONEHOLM_MAX_PIECES.getAsInt() && ms < PatchworkSpec.STONEHOLM_MAX_MILLIS.getAsInt()) {
            return false;
        }
        patchwork$stopped = true;
        patchwork$LOG.warn("[Patchwork] stoneholmbudget: Stoneholm village stopped growing at {} pieces after {} ms ({}); finished as it stands",
                pieces, ms, stopping ? "server stopping" : "budget reached");
        return true;
    }

    @Inject(method = "generatePiece", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void patchwork$pieceBudget(CallbackInfo ci) {
        if (patchwork$overBudget()) {
            ci.cancel();
            patchwork$done();
        }
    }

    @Inject(method = "tryPlacePiece*", at = @At("HEAD"), cancellable = true, remap = false, require = 2)
    private void patchwork$placeBudget(CallbackInfoReturnable<Boolean> cir) {
        if (patchwork$overBudget()) cir.setReturnValue(false);
    }

    @Inject(method = "generatePiece", at = @At("RETURN"), remap = false, require = 1)
    private void patchwork$after(CallbackInfo ci) {
        patchwork$done();
    }

    /** The village is complete once the last queued piece is handled; logged only on test runs. */
    @Unique
    private void patchwork$done() {
        if (patchwork$LOG_ALL && structurePieces.isEmpty()) {
            patchwork$LOG.info("[Patchwork] stoneholmbudget: village done, {} pieces in {} ms{}", children.size(),
                    (System.nanoTime() - patchwork$start) / 1_000_000L, patchwork$stopped ? " (budget reached)" : "");
        }
    }
}
