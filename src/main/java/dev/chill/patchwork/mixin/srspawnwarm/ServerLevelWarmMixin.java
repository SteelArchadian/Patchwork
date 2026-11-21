package dev.chill.patchwork.mixin.srspawnwarm;

import dev.chill.patchwork.srspawnwarm.SpawnWarm;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Starts Streams Reflowing's river engine as soon as the overworld exists; see {@link SpawnWarm}. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWarmMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void patchwork$warmRivers(CallbackInfo ci) {
        SpawnWarm.warm((ServerLevel) (Object) this, "level created");
    }
}
