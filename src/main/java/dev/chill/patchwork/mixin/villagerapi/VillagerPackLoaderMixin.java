package dev.chill.patchwork.mixin.villagerapi;

import com.frikinjay.villagerapi.villagerpack.VillagerPackLoader;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.chill.patchwork.villagerapi.VillagerPackCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

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
}
