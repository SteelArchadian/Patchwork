// SPDX-License-Identifier: LGPL-3.0-or-later
// Adapted from LGPL-3.0 code; see NOTICE.md. Patchwork as a whole is LGPL-3.0-only.
package dev.chill.patchwork.mixin.biolith;

import com.mojang.logging.LogUtils;
import com.terraformersmc.biolith.api.biome.BiolithFittestNodes;
import com.terraformersmc.biolith.impl.biome.SimpleArrayIterator;
import dev.chill.patchwork.biolith.SearchState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replaces Biolith 3.0.14's {@code biolith$searchTreeGet} (merged into {@link Climate.RTree} by Biolith's
 * MixinSearchTree; priority 1500 makes this copy win). The search, its pruning and its per-(tree, thread)
 * starting guesses are unchanged. Per call, the original allocates a 64-slot iterator array, one iterator per
 * tree level, the parameter array and a fallback key, and writes two ThreadLocals; this version reuses one
 * per-thread {@link SearchState} and allocates only the returned result.
 *
 * With {@code -Dpatchwork.verify.biolith=true} every lookup is also run through a verbatim copy of the
 * original algorithm (with its own, independent guesses) and any difference is logged.
 */
@Mixin(value = Climate.RTree.class, priority = 1500)
public abstract class RTreeSearchMixin<T> {
    @Unique
    private static final Logger patchwork$LOG = LogUtils.getLogger();
    @Unique
    private static final boolean patchwork$VERIFY = Boolean.getBoolean("patchwork.verify.biolith");
    @Unique
    private static final AtomicLong patchwork$checked = new AtomicLong();
    @Unique
    private static final AtomicLong patchwork$mismatches = new AtomicLong();
    @Unique
    private static final ResourceKey<?> patchwork$NULL_KEY =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("biolith", "null"));

    @Shadow
    @Final
    private Climate.RTree.Node<T> root;

    @Unique
    private final ThreadLocal<SearchState<T>> patchwork$state = ThreadLocal.withInitial(SearchState::new);
    @Unique
    private final ThreadLocal<Climate.RTree.Leaf<T>> patchwork$refUltimate = new ThreadLocal<>();
    @Unique
    private final ThreadLocal<Climate.RTree.Leaf<T>> patchwork$refPenultimate = new ThreadLocal<>();

    public BiolithFittestNodes<T> biolith$searchTreeGet(Climate.TargetPoint point, Climate.DistanceMetric<T> distanceFunction) {
        BiolithFittestNodes<T> result = patchwork$search(point, distanceFunction);
        if (patchwork$VERIFY) {
            patchwork$verify(result, patchwork$reference(point, distanceFunction));
        }
        return result;
    }

    @Unique
    private BiolithFittestNodes<T> patchwork$search(Climate.TargetPoint point, Climate.DistanceMetric<T> distanceFunction) {
        SearchState<T> state = patchwork$state.get();
        long[] otherParameters = state.parametersOf(point);
        Climate.RTree.Node<T> node = this.root;
        long nodeDistance;
        Climate.RTree.Leaf<T> ultimate = state.previousUltimate;
        Climate.RTree.Leaf<T> penultimate = state.previousPenultimate;
        long ultimateDistance = ultimate != null ? distanceFunction.distance(ultimate, otherParameters) : Long.MAX_VALUE;
        long penultimateDistance = penultimate != null ? distanceFunction.distance(penultimate, otherParameters) : Long.MAX_VALUE;
        if (ultimateDistance > penultimateDistance) {
            Climate.RTree.Leaf<T> temp = ultimate;
            ultimate = penultimate;
            penultimate = temp;
            long tempDistance = ultimateDistance;
            ultimateDistance = penultimateDistance;
            penultimateDistance = tempDistance;
        }

        if (!(node instanceof Climate.RTree.SubTree<T> branchNode)) {
            if (node instanceof Climate.RTree.Leaf<T> leafNode) {
                return new BiolithFittestNodes<>(leafNode, distanceFunction.distance(leafNode, otherParameters));
            }
            patchwork$LOG.error("No biomes are available in MultiNoiseBiomeSource!");
            return new BiolithFittestNodes<>(ultimate, ultimateDistance);
        }

        Climate.RTree.Node<T>[][] children = state.children;
        int[] next = state.next;
        int depth = 0;
        children[0] = branchNode.children;
        next[0] = 0;

        while (next[depth] < children[depth].length) {
            node = children[depth][next[depth]++];

            for (nodeDistance = distanceFunction.distance(node, otherParameters);
                 node instanceof Climate.RTree.SubTree<T> subTree;
                 nodeDistance = distanceFunction.distance(node, otherParameters)) {
                if (penultimateDistance <= nodeDistance) {
                    break;
                }
                depth++;
                children[depth] = subTree.children;
                next[depth] = 0;
                node = children[depth][next[depth]++];
            }

            if (node instanceof Climate.RTree.Leaf<T> leafNode && penultimateDistance > nodeDistance) {
                if (ultimateDistance > nodeDistance) {
                    if (!patchwork$keyOf(leafNode).location().equals(patchwork$keyOf(ultimate).location())) {
                        penultimateDistance = ultimateDistance;
                        penultimate = ultimate;
                    }
                    ultimateDistance = nodeDistance;
                    ultimate = leafNode;
                } else if (!patchwork$keyOf(leafNode).location().equals(patchwork$keyOf(ultimate).location())) {
                    penultimateDistance = nodeDistance;
                    penultimate = leafNode;
                }
            }

            while (depth > 0 && next[depth] >= children[depth].length) {
                depth--;
            }
        }

        state.previousUltimate = ultimate;
        state.previousPenultimate = penultimate;
        return penultimate == null
                ? new BiolithFittestNodes<>(ultimate, ultimateDistance)
                : new BiolithFittestNodes<>(ultimate, ultimateDistance, penultimate, penultimateDistance);
    }

    @Unique
    private static ResourceKey<?> patchwork$keyOf(Climate.RTree.Leaf<?> leafNode) {
        if (leafNode == null) {
            return patchwork$NULL_KEY;
        }
        Object value = leafNode.value;
        if (value instanceof Holder.Reference<?> reference) {
            return reference.key();
        }
        return ((Holder<?>) value).unwrapKey().orElseThrow();
    }

    /** Verbatim Biolith 3.0.14 algorithm (allocation included), used only in verify mode. */
    @Unique
    @SuppressWarnings("unchecked")
    private BiolithFittestNodes<T> patchwork$reference(Climate.TargetPoint point, Climate.DistanceMetric<T> distanceFunction) {
        long[] otherParameters = point.toParameterArray();
        Climate.RTree.Node<T> node = this.root;
        long nodeDistance;
        Iterator<Climate.RTree.Node<T>>[] stack = new SimpleArrayIterator[64];
        int stackDepth = 0;
        Climate.RTree.Leaf<T> ultimate = patchwork$refUltimate.get();
        Climate.RTree.Leaf<T> penultimate = patchwork$refPenultimate.get();
        long ultimateDistance = ultimate != null ? distanceFunction.distance(ultimate, otherParameters) : Long.MAX_VALUE;
        long penultimateDistance = penultimate != null ? distanceFunction.distance(penultimate, otherParameters) : Long.MAX_VALUE;
        if (ultimateDistance > penultimateDistance) {
            Climate.RTree.Leaf<T> temp = ultimate;
            ultimate = penultimate;
            penultimate = temp;
            long tempDistance = ultimateDistance;
            ultimateDistance = penultimateDistance;
            penultimateDistance = tempDistance;
        }
        if (!(node instanceof Climate.RTree.SubTree<T> branchNode)) {
            if (node instanceof Climate.RTree.Leaf<T> leafNode) {
                return new BiolithFittestNodes<>(leafNode, distanceFunction.distance(leafNode, otherParameters));
            }
            return new BiolithFittestNodes<>(ultimate, ultimateDistance);
        }
        stack[stackDepth] = new SimpleArrayIterator<>(branchNode.children);
        while (stack[stackDepth].hasNext()) {
            node = stack[stackDepth].next();
            for (nodeDistance = distanceFunction.distance(node, otherParameters);
                 node instanceof Climate.RTree.SubTree<T> subTree;
                 nodeDistance = distanceFunction.distance(node, otherParameters)) {
                if (penultimateDistance <= nodeDistance) {
                    break;
                }
                stack[++stackDepth] = new SimpleArrayIterator<>(subTree.children);
                node = stack[stackDepth].next();
            }
            if (node instanceof Climate.RTree.Leaf<T> leafNode && penultimateDistance > nodeDistance) {
                if (ultimateDistance > nodeDistance) {
                    if (!patchwork$refKeyOf(leafNode).location().equals(patchwork$refKeyOf(ultimate).location())) {
                        penultimateDistance = ultimateDistance;
                        penultimate = ultimate;
                    }
                    ultimateDistance = nodeDistance;
                    ultimate = leafNode;
                } else if (!patchwork$refKeyOf(leafNode).location().equals(patchwork$refKeyOf(ultimate).location())) {
                    penultimateDistance = nodeDistance;
                    penultimate = leafNode;
                }
            }
            while (stackDepth > 0 && !stack[stackDepth].hasNext()) {
                stackDepth--;
            }
        }
        patchwork$refUltimate.set(ultimate);
        patchwork$refPenultimate.set(penultimate);
        return penultimate == null
                ? new BiolithFittestNodes<>(ultimate, ultimateDistance)
                : new BiolithFittestNodes<>(ultimate, ultimateDistance, penultimate, penultimateDistance);
    }

    @Unique
    private static ResourceKey<?> patchwork$refKeyOf(Climate.RTree.Leaf<?> leafNode) {
        return leafNode == null
                ? ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("biolith", "null"))
                : ((Holder<?>) leafNode.value).unwrapKey().orElseThrow();
    }

    @Unique
    private static void patchwork$verify(BiolithFittestNodes<?> fast, BiolithFittestNodes<?> reference) {
        long checked = patchwork$checked.incrementAndGet();
        boolean same = fast.ultimate() == reference.ultimate()
                && fast.ultimateDistance() == reference.ultimateDistance()
                && fast.penultimate() == reference.penultimate()
                && fast.penultimateDistance() == reference.penultimateDistance();
        if (!same) {
            long n = patchwork$mismatches.incrementAndGet();
            if (n <= 20) {
                patchwork$LOG.warn("[Patchwork/biolith] Biolith search mismatch #{}: fast {} vs reference {}", n,
                        Objects.toString(fast), Objects.toString(reference));
            }
        }
        if ((checked & ((1 << 22) - 1)) == 0) {
            patchwork$LOG.info("[Patchwork/biolith] Biolith verify: {} lookups checked, {} mismatches", checked, patchwork$mismatches.get());
        }
    }
}
