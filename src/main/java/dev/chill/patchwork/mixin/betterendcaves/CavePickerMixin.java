package dev.chill.patchwork.mixin.betterendcaves;

import dev.chill.patchwork.betterendcaves.CavePickerGuard;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandles;

/** See CavePickerGuard. Merged into Better End's EndBiomes, so lookupClass() is EndBiomes itself. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.registry.EndBiomes", remap = false)
public abstract class CavePickerMixin {
    @Inject(method = "onServerLevelReady", at = @At("HEAD"), remap = false)
    private static void patchwork$levelReady(ServerLevel level, ResourceKey<Level> key, LevelStem stem, long seed, CallbackInfo ci) {
        CavePickerGuard.levelReady(MethodHandles.lookup().lookupClass(), level, key, stem, seed);
    }

    @Inject(method = "onServerLevelReady", at = @At("TAIL"), remap = false)
    private static void patchwork$testClear(ServerLevel level, ResourceKey<Level> key, LevelStem stem, long seed, CallbackInfo ci) {
        CavePickerGuard.testClear(MethodHandles.lookup().lookupClass());
        CavePickerGuard.afterReady();
    }

    @Inject(method = "getCaveBiome", at = @At("HEAD"), remap = false)
    private static void patchwork$check(int x, int z, CallbackInfoReturnable<Object> cir) {
        if (!CavePickerGuard.settled) CavePickerGuard.check();
    }
}
