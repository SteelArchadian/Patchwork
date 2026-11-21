package dev.chill.patchwork.slabbedleaves;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

/**
 * Slabbed and Traversable Leaves both @Redirect the same getCollisionShape call in BlockCollisions.computeNext. The
 * second one finds nothing left to redirect and the game crashes on the first player tick ("Scanned 0 target(s)").
 * Both mixins are cancelled (through MixinSquared, which other mods bundle) and Patchwork's one redirect does both
 * jobs: Traversable Leaves' rules for leaves, Slabbed's lowered-block shapes for everything else.
 */
public final class SlabbedLeaves {
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    static final Set<String> CANCELLED = Set.of(
            "com.slabbed.mixin.BlockCollisionsLoweredAboveMixin",
            "com.dreu.traversableleaves.mixin.BlockCollisionsMixin");
    private static volatile boolean registered;

    private SlabbedLeaves() {
    }

    public static boolean registered() {
        return registered;
    }

    /** Registers the canceller; false (module off, both mods left as they are) if MixinSquared is not loaded. */
    public static synchronized boolean register(ClassLoader loader) {
        if (registered) return true;
        try {
            Class<?> api = find("com.bawnorton.mixinsquared.api.MixinCanceller", loader);
            Class<?> registrar = find("com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar", loader);
            Object canceller = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "shouldCancel": {
                        String mixin = (String) args[1];
                        boolean cancel = mixin != null && CANCELLED.contains(mixin.replace('/', '.'));
                        if (cancel) {
                            LOG.info("[Patchwork] slabbedleaves: {} replaced by Patchwork's combined collision hook", mixin);
                        }
                        return cancel;
                    }
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "Patchwork slabbedleaves canceller";
                    default: return null;
                }
            });
            Method register = registrar.getMethod("register", api);
            register.invoke(null, canceller);
            registered = true;
            LOG.info("[Patchwork] slabbedleaves: Slabbed + Traversable Leaves collision hooks combined");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[Patchwork] slabbedleaves: MixinSquared not found, cannot combine the two collision hooks ({})",
                    e.toString());
        }
        return registered;
    }

    private static Class<?> find(String name, ClassLoader loader) throws ClassNotFoundException {
        for (ClassLoader cl : List.of(loader, Thread.currentThread().getContextClassLoader(), SlabbedLeaves.class.getClassLoader())) {
            if (cl == null) continue;
            try {
                return Class.forName(name, false, cl);
            } catch (ClassNotFoundException ignored) {
                // try the next loader
            }
        }
        return Class.forName(name);
    }
}
