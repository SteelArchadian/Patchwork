package dev.chill.patchwork.mixin.villagerapi;

import com.frikinjay.villagerapi.villagerpack.VillagerPackLoader;
import dev.chill.patchwork.villagerapi.NoConfigPacks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/** Records which zips and mods have no villager pack config (see {@link NoConfigPacks}). */
@Mixin(value = VillagerPackLoader.VillagerPack.class, remap = false)
public abstract class VillagerPackZipMixin {
    @Shadow @Final private Path sourcePath;
    @Shadow @Final private boolean fromModsDirectory;

    @Shadow public abstract boolean hasConfig();

    @Inject(method = "<init>(Ljava/nio/file/Path;Ljava/lang/String;Z)V", at = @At("TAIL"), require = 1)
    private void patchwork$remember(Path path, String sourceName, boolean fromMods, CallbackInfo ci) {
        if (fromModsDirectory) NoConfigPacks.modOpened(sourceName, hasConfig());
        else NoConfigPacks.opened(sourcePath, hasConfig());
    }
}
