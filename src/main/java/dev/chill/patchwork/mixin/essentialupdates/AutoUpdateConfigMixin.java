package dev.chill.patchwork.mixin.essentialupdates;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Essential from updating itself or offering updates, in every profile. Essential's loader reads
 * {@code autoUpdate} from essential/essential-loader.properties and its stage2 file; a new profile has neither, so
 * Essential writes its default (update, or update after a prompt) and starts nagging. Every read of that key now
 * sees "false" and the files are set to "false" too, so the loader also stops on the next start. A downloaded but
 * not yet applied update ({@code pendingUpdateVersion}) is hidden, so no update flag or popup shows for it.
 */
@Pseudo
@Mixin(targets = "gg.essential.util.AutoUpdate", remap = false)
public abstract class AutoUpdateConfigMixin {
    @Inject(method = "getContainerConfigValue", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private void patchwork$noAutoUpdate(Path config, String key, CallbackInfoReturnable<String> cir) {
        if ("pendingUpdateVersion".equals(key)) {
            cir.setReturnValue(null);
        } else if ("autoUpdate".equals(key) && !"false".equals(cir.getReturnValue())) {
            patchwork$writeFalse(config);
            cir.setReturnValue("false");
        }
    }

    @Unique
    private static void patchwork$writeFalse(Path config) {
        try {
            Properties properties = new Properties();
            if (Files.exists(config)) {
                try (Reader reader = Files.newBufferedReader(config)) {
                    properties.load(reader);
                }
            } else {
                Files.createDirectories(config.getParent());
            }
            properties.setProperty("autoUpdate", "false");
            try (Writer writer = Files.newBufferedWriter(config)) {
                properties.store(writer, null);
            }
            LoggerFactory.getLogger("Patchwork").info("[Patchwork/essentialupdates] Essential auto-update off in {}", config.getFileName());
        } catch (Exception e) {
            LoggerFactory.getLogger("Patchwork").warn("[Patchwork/essentialupdates] could not write {}", config, e);
        }
    }
}
