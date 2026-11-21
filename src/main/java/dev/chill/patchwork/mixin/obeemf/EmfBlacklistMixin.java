package dev.chill.patchwork.mixin.obeemf;

import dev.chill.patchwork.obeemf.EmfCustomBlocks;
import fr.madu59.obe.client.compat.blacklist.Blacklist;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks found to have an EMF custom model (see {@link EmfPartQuadsMixin}) count as blacklisted in OBE. */
@Mixin(value = Blacklist.class, remap = false)
public abstract class EmfBlacklistMixin {
    @Inject(method = "isBlacklisted", at = @At("HEAD"), cancellable = true, require = 1)
    private static void patchwork$emfCustom(BlockState state, String group, CallbackInfoReturnable<Boolean> cir) {
        if (EmfCustomBlocks.contains(state.getBlock())) {
            cir.setReturnValue(true);
        }
    }

    /** OBE reloads its blacklist with the resource packs; EMF's models may have changed with them. */
    @Inject(method = "load", at = @At("HEAD"), require = 0)
    private static void patchwork$recheck(CallbackInfo ci) {
        EmfCustomBlocks.clear();
    }
}
