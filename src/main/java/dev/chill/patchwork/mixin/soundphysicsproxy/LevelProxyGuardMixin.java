package dev.chill.patchwork.mixin.soundphysicsproxy;

import com.sonicether.soundphysics.world.ClientLevelProxy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sound Physics Remastered works out each sound's echo and muffling from a copy of the world it keeps. Right after
 * joining a world (and on some dimension changes) that copy isn't made yet, so every sound in that moment threw a
 * NullPointerException on the sound thread and was dropped. Until the copy exists, sounds now play as if Sound Physics
 * were switched off (no echo or muffling for that second), the same path Sound Physics takes when disabled.
 */
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class LevelProxyGuardMixin {
    @Shadow
    private static ClientLevelProxy getLevelProxy() {
        throw new AssertionError();
    }

    @Inject(method = "evaluateEnvironment", at = @At("HEAD"), cancellable = true, require = 1)
    private static void patchwork$waitForLevelCopy(int sourceID, double posX, double posY, double posZ, SoundSource category,
                                                  ResourceLocation sound, float directCutoff, CallbackInfoReturnable<Vec3> cir) {
        if (getLevelProxy() == null) {
            cir.setReturnValue(null);
        }
    }
}
