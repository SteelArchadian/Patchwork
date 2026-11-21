package dev.chill.patchwork.emfdefer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * Entity Model Features parses and compiles every model's animations on the render thread while the resource reload
 * builds entity renderers, which freezes the loading screen. During the loading screen those setups are queued instead
 * and run afterwards on the same thread, a few milliseconds per frame, with each model's variant state restored as the
 * immediate call would have left it. Everything still queued runs at once before any entity is drawn or a world is
 * shown, so no model is ever drawn without its animations. Same work, same thread, same results; only the timing moves.
 * {@code -Dpatchwork.verify.emfdefer=true} keeps EMF's own timing and logs the same digest for comparison.
 */
public final class EmfDefer {
    private static final Logger LOG = LoggerFactory.getLogger("Patchwork");
    private static final boolean VERIFY = Boolean.getBoolean("patchwork.verify.emfdefer");
    private static final long SLICE_NS = 4_000_000L;

    private record Pending(EMFJemData jem, EMFModelPartRoot root, int variant) {}

    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
    /** Which model variants received an animation handler this reload, in order: compared between normal and verify runs. */
    private static final List<String> RECEIVED = new ArrayList<>();
    private static boolean running;
    private static int deferred;
    private static long drainNs;
    private static boolean reported;

    private EmfDefer() {}

    /** From the mixin at the head of EMFManager.setupAnimationsFromJemToModel: true when the call was queued. */
    public static boolean defer(EMFJemData jem, EMFModelPartRoot root, int variant) {
        if (VERIFY || running) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() == null || !mc.isSameThread()) return false;
        // The only state the original leaves behind before parsing: the root switched to this variant.
        root.setVariantStateTo(variant);
        QUEUE.add(new Pending(jem, root, variant));
        deferred++;
        return true;
    }

    /** EMF starts over for a resource reload: setups queued for the old models are void. */
    public static void reset() {
        QUEUE.clear();
        RECEIVED.clear();
        deferred = 0;
        drainNs = 0;
        reported = false;
    }

    public static void received(EMFModelPartRoot root, int variant) {
        RECEIVED.add(root.modelName.getfileName() + "#" + variant);
    }

    /** Every frame: nothing while the loading screen is up, a slice on menus, everything once a world is shown. */
    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (mc.level != null) {
            drainAll();
        } else if (!QUEUE.isEmpty()) {
            run(System.nanoTime() + SLICE_NS);
        }
        if (QUEUE.isEmpty() && !reported && (deferred > 0 || (VERIFY && mc.level != null))) report();
    }

    /** Before any entity is drawn, including in menus. */
    public static void drainAll() {
        if (!QUEUE.isEmpty() && !running) run(Long.MAX_VALUE);
    }

    private static void run(long deadline) {
        running = true;
        long start = System.nanoTime();
        try {
            while (!QUEUE.isEmpty() && System.nanoTime() < deadline) {
                Pending p = QUEUE.poll();
                int before = p.root.currentModelVariant;
                try {
                    EMFManager.getInstance().setupAnimationsFromJemToModel(p.jem, p.root, p.variant);
                } catch (Throwable t) {
                    LOG.error("[Patchwork] emfdefer: animation setup failed for {} variant {}", p.root.modelName, p.variant, t);
                } finally {
                    p.root.setVariantStateTo(before);
                }
            }
        } finally {
            running = false;
            drainNs += System.nanoTime() - start;
        }
    }

    private static void report() {
        reported = true;
        LOG.info("[Patchwork] emfdefer: {} {} animation setups ({} ms on menus), {} handlers, digest {}",
                VERIFY ? "verify: ran" : "moved off the loading screen:", VERIFY ? RECEIVED.size() : deferred,
                drainNs / 1_000_000, RECEIVED.size(), Integer.toHexString(RECEIVED.stream().sorted().toList().hashCode()));
    }
}
