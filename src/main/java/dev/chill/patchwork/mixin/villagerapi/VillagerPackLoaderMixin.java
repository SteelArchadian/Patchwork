package dev.chill.patchwork.mixin.villagerapi;

import com.frikinjay.villagerapi.villagerpack.VillagerPackLoader;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import dev.chill.patchwork.villagerapi.NoConfigPacks;
import dev.chill.patchwork.villagerapi.VillagerPackCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.List;

/** See {@link VillagerPackCache}: one pack discovery per packs folder while a world runs. */
@Mixin(value = VillagerPackLoader.class, remap = false)
public abstract class VillagerPackLoaderMixin {
    @Shadow @Final private Path villagerPacksDir;

    @WrapMethod(method = "getDiscoveredPacks")
    private List<VillagerPackLoader.VillagerPack> patchwork$discoverOnce(Operation<List<VillagerPackLoader.VillagerPack>> original) {
        return VillagerPackCache.get(villagerPacksDir, original::call);
    }

    /** Zips already seen without a villager pack config are not opened again ({@link NoConfigPacks}). */
    @Inject(method = "isPackCandidate", at = @At("HEAD"), cancellable = true, require = 1)
    private static void patchwork$skipKnownWithoutConfig(Path path, CallbackInfoReturnable<Boolean> cir) {
        if (NoConfigPacks.knownWithout(path)) cir.setReturnValue(false);
    }

    /** Mods already seen without a villager pack config are not looked at again ({@link NoConfigPacks}). */
    @WrapOperation(method = "discoverAll", at = @At(value = "INVOKE",
            target = "Lcom/frikinjay/villagerapi/platform/CommonPlatformHelper;getAllModRootPaths()Ljava/util/Map;"), require = 1)
    private static Map<String, Path> patchwork$modsWithConfig(Operation<Map<String, Path>> original) {
        return NoConfigPacks.modRoots(original::call);
    }
}
