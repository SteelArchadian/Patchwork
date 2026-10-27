package dev.chill.patchwork.mixin.midnightlib;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.chill.patchwork.midnightlib.ConfigLock;
import org.spongepowered.asm.mixin.Mixin;

/**
 * MidnightLib 1.9.3 keeps every mod's config entries in one static LinkedHashMap. NeoForge constructs mods in
 * parallel, so when two MidnightLib mods call {@code MidnightConfig.init} at once, one adds entries while the other
 * iterates them in {@code loadValuesFromJson}: ConcurrentModificationException, the mod fails to construct, and the
 * whole launch fails (about 1 in 9 launches on a large pack). This makes init, load and write take turns on one lock.
 * Each call does exactly what it did before; only the overlap is removed. The lock is reentrant (init calls
 * loadValuesFromJson, which may call writeChanges).
 */
@Mixin(targets = "eu.midnightdust.lib.config.MidnightConfig", remap = false)
public abstract class MidnightConfigMixin {

    @WrapMethod(method = "init(Ljava/lang/String;Ljava/lang/Class;)V")
    private static void patchwork$serializeInit(String modid, Class<?> config, Operation<Void> original) {
        synchronized (ConfigLock.LOCK) {
            original.call(modid, config);
        }
    }

    @WrapMethod(method = "loadValuesFromJson()V")
    private void patchwork$serializeLoad(Operation<Void> original) {
        synchronized (ConfigLock.LOCK) {
            original.call();
        }
    }

    @WrapMethod(method = "write(Ljava/lang/String;)V")
    private static void patchwork$serializeWrite(String modid, Operation<Void> original) {
        synchronized (ConfigLock.LOCK) {
            original.call(modid);
        }
    }

    @WrapMethod(method = "writeChanges(Ljava/lang/String;)V")
    private void patchwork$serializeWriteChanges(String modid, Operation<Void> original) {
        synchronized (ConfigLock.LOCK) {
            original.call(modid);
        }
    }
}
