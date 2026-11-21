package dev.chill.patchwork.mixin.srspawnwarm;

import dev.chill.patchwork.srspawnwarm.SpawnWarm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prefetches rivers around the real spawn (a new world picks it after the level is created); see {@link SpawnWarm}. */
@Mixin(MinecraftServer.class)
public abstract class PrepareLevelsWarmMixin {
    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void patchwork$warmSpawnRivers(ChunkProgressListener listener, CallbackInfo ci) {
        SpawnWarm.warm(((MinecraftServer) (Object) this).overworld(), "spawn");
    }
}
