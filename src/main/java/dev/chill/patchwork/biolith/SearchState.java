package dev.chill.patchwork.biolith;

import net.minecraft.world.level.biome.Climate;

/**
 * Per-thread, per-search-tree working memory for Biolith's biome search. Biolith keeps the previous ultimate and
 * penultimate leaf per (tree, thread) as starting guesses; this keeps exactly those, plus reusable buffers, so a
 * lookup allocates only its result.
 */
public final class SearchState<T> {
    /** Deeper than any vanilla or modded biome tree (Biolith itself uses a fixed 64-entry stack). */
    public static final int MAX_DEPTH = 64;

    public Climate.RTree.Leaf<T> previousUltimate;
    public Climate.RTree.Leaf<T> previousPenultimate;
    public final long[] parameters = new long[7];
    @SuppressWarnings("unchecked")
    public final Climate.RTree.Node<T>[][] children = new Climate.RTree.Node[MAX_DEPTH][];
    public final int[] next = new int[MAX_DEPTH];

    /** Same values as {@link Climate.TargetPoint#toParameterArray()}, without allocating. */
    public long[] parametersOf(Climate.TargetPoint point) {
        long[] p = parameters;
        p[0] = point.temperature();
        p[1] = point.humidity();
        p[2] = point.continentalness();
        p[3] = point.erosion();
        p[4] = point.depth();
        p[5] = point.weirdness();
        p[6] = 0L;
        return p;
    }
}
