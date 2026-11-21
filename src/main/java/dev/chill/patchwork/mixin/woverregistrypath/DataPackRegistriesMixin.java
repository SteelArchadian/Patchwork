package dev.chill.patchwork.mixin.woverregistrypath;

import dev.chill.patchwork.woverregistrypath.WoverRegistryPath;
import net.minecraft.resources.RegistryDataLoader;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

/** See WoverRegistryPath: adds WorldWeaver's datapack registries NeoForge's list never picked up. */
@Mixin(value = DataPackRegistriesHooks.class, remap = false)
public abstract class DataPackRegistriesMixin {
    @Shadow @Final private static List<RegistryDataLoader.RegistryData<?>> DATA_PACK_REGISTRIES;

    @Inject(method = "getDataPackRegistries", at = @At("HEAD"), remap = false)
    private static void patchwork$addWover(CallbackInfoReturnable<List<RegistryDataLoader.RegistryData<?>>> cir) {
        WoverRegistryPath.addMissing(DATA_PACK_REGISTRIES);
    }

    @Inject(method = "getDataPackRegistriesWithDimensions", at = @At("HEAD"), remap = false)
    private static void patchwork$addWoverDim(CallbackInfoReturnable<Stream<RegistryDataLoader.RegistryData<?>>> cir) {
        WoverRegistryPath.addMissing(DATA_PACK_REGISTRIES);
    }
}
