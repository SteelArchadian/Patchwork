package dev.chill.patchwork.mixin.earlyconfigtick;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.loading.ClientModLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Some mods read their NeoForge config in a client tick listener. While a config is not loaded yet (early in startup,
 * or for a moment while NeoForge reloads a changed config file) ConfigValue.get throws
 * "Cannot get config value before config is loaded." and the whole game crashes. Here that one exception is caught
 * for the client tick event only: the rest of that tick's listeners are skipped and the next tick runs normally.
 * Each offending listener is logged once so its owner can fix it. Any other exception still crashes as before.
 * Root cause seen in the pack: the first client ticks run while NeoForge is still loading mods (configs not loaded,
 * key mappings not created yet). So while ClientModLoader.isLoading() the client tick event is not posted at all;
 * listeners start getting ticks once mod loading has finished, the same moment they could first act anyway.
 */
@Mixin(value = ClientHooks.class, remap = false)
public abstract class ClientTickGuardMixin {
    @Unique private static final Logger patchwork$LOG = LoggerFactory.getLogger("Patchwork");
    @Unique private static final String patchwork$NOT_LOADED = "Cannot get config value before config is loaded";
    @Unique private static final Set<String> patchwork$reported = ConcurrentHashMap.newKeySet();
    @Unique private static int patchwork$heldTicks;

    @WrapOperation(method = {"fireClientTickPre", "fireClientTickPost"},
            at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
            remap = false, require = 0)
    private static Event patchwork$guardConfigRead(IEventBus bus, Event event, Operation<Event> original) {
        if (ClientModLoader.isLoading()) {
            if (patchwork$heldTicks++ == 0) {
                patchwork$LOG.info("[Patchwork] earlyconfigtick: holding client tick events until mod loading finishes");
            }
            return event;
        }
        if (patchwork$heldTicks > 0) {
            patchwork$LOG.info("[Patchwork] earlyconfigtick: mod loading finished, held {} client tick events", patchwork$heldTicks);
            patchwork$heldTicks = 0;
        }
        try {
            return original.call(bus, event);
        } catch (IllegalStateException e) {
            if (e.getMessage() == null || !e.getMessage().startsWith(patchwork$NOT_LOADED)) throw e;
            String where = patchwork$culprit(e);
            if (patchwork$reported.add(where)) {
                patchwork$LOG.warn("[Patchwork] earlyconfigtick: {} read a config before it was loaded; skipped that client tick instead of crashing", where);
            }
            return event;
        }
    }

    /** First stack frame outside NeoForge, the event bus and Guava: the listener that read the config. */
    @Unique
    private static String patchwork$culprit(Throwable e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            String c = frame.getClassName();
            if (c.startsWith("net.neoforged.") || c.startsWith("com.google.common.") || c.startsWith("java.")) continue;
            return c + "." + frame.getMethodName();
        }
        return "unknown listener";
    }
}
