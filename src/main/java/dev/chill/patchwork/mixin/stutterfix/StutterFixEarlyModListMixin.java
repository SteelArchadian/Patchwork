package dev.chill.patchwork.mixin.stutterfix;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * StutterFix 0.2.4 hooks Util.backgroundExecutor, and the first use loads StutterFix, whose static initializer asks
 * ModList.get().isLoaded("vulkanmod"). ModList only exists once mods are constructed; a Fabric mod run through
 * Connector (Immersive Portals' client entrypoint) uses the background executor earlier, so ModList.get() is null and
 * the game crashes at start. Before ModList exists, the same answer comes from the loading mod list.
 */
@Mixin(targets = "net.wisecase2.stutterfix.StutterFix", remap = false)
public abstract class StutterFixEarlyModListMixin {

    @Redirect(method = "<clinit>", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/neoforged/fml/ModList;isLoaded(Ljava/lang/String;)Z"))
    private static boolean patchwork$isLoadedEarly(ModList list, String modId) {
        if (list != null) {
            return list.isLoaded(modId);
        }
        return LoadingModList.get().getModFileById(modId) != null;
    }
}
