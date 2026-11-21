package dev.chill.patchwork.mixin.relativeblocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Olaf's Relatively Placed Blocks draws a cluster (small blocks sharing one space) only through Fabric's custom model
 * hook. On NeoForge with Sodium nothing calls that hook, and the cluster's own model is empty, so every cluster was
 * invisible. This adds NeoForge's model calls to the cluster model: it reads the members from the block entity the
 * same way the mod does, and returns each member's own quads moved to its cell, tinted as the member would be.
 */
@Mixin(targets = "com.lukeolafp.relativeblocks.blocks.BlockClusterBakedModel", remap = false)
public abstract class ClusterModelMixin {
    @Unique private static final ModelProperty<Cluster> PATCHWORK$CLUSTER = new ModelProperty<>();
    @Unique private static final int PATCHWORK$TINTS = 4;
    @Unique private static Method patchwork$renderData, patchwork$layout, patchwork$cells, patchwork$offX, patchwork$offY, patchwork$offZ;
    @Unique private static boolean patchwork$looked;

    @Unique
    private record Cluster(BlockState[] cells, float[][] offsets, int[][] tints) {}

    @Unique
    private static boolean patchwork$lookUp() {
        if (!patchwork$looked) {
            patchwork$looked = true;
            try {
                Class<?> be = Class.forName("com.lukeolafp.relativeblocks.blocks.BlockClusterBlockEntity");
                Class<?> data = Class.forName("com.lukeolafp.relativeblocks.blocks.BlockClusterBlockEntity$RenderData");
                patchwork$renderData = be.getMethod("getRenderData");
                patchwork$layout = data.getMethod("layout");
                patchwork$cells = data.getMethod("cells");
                patchwork$offX = be.getMethod("cellOffsetX", int.class, int.class);
                patchwork$offY = be.getMethod("cellOffsetY", int.class, int.class);
                patchwork$offZ = be.getMethod("cellOffsetZ", int.class, int.class);
            } catch (ReflectiveOperationException e) {
                patchwork$renderData = null;
            }
        }
        return patchwork$renderData != null;
    }

    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !patchwork$lookUp() || !patchwork$renderData.getDeclaringClass().isInstance(be)) {
            return modelData;
        }
        try {
            Object data = patchwork$renderData.invoke(be);
            int layout = (int) patchwork$layout.invoke(data);
            BlockState[] cells = ((BlockState[]) patchwork$cells.invoke(data)).clone();
            float[][] offsets = new float[cells.length][];
            int[][] tints = new int[cells.length][];
            var colors = Minecraft.getInstance().getBlockColors();
            for (int cell = 0; cell < cells.length; cell++) {
                if (cells[cell] == null) {
                    continue;
                }
                offsets[cell] = new float[] {
                        ((Number) patchwork$offX.invoke(null, layout, cell)).floatValue(),
                        ((Number) patchwork$offY.invoke(null, layout, cell)).floatValue(),
                        ((Number) patchwork$offZ.invoke(null, layout, cell)).floatValue()};
                tints[cell] = new int[PATCHWORK$TINTS];
                for (int i = 0; i < PATCHWORK$TINTS; i++) {
                    tints[cell][i] = 0xFF000000 | colors.getColor(cells[cell], level, pos, i);
                }
            }
            return modelData.derive().with(PATCHWORK$CLUSTER, new Cluster(cells, offsets, tints)).build();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return modelData;
        }
    }

    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        Cluster cluster = data.get(PATCHWORK$CLUSTER);
        if (cluster == null) {
            return ChunkRenderTypeSet.none();
        }
        List<ChunkRenderTypeSet> sets = new ArrayList<>();
        for (BlockState member : cluster.cells()) {
            if (member != null) {
                sets.add(patchwork$model(member).getRenderTypes(member, rand, ModelData.EMPTY));
            }
        }
        return ChunkRenderTypeSet.union(sets);
    }

    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        Cluster cluster = data.get(PATCHWORK$CLUSTER);
        if (cluster == null) {
            return List.of();
        }
        List<BakedQuad> out = new ArrayList<>();
        for (int cell = 0; cell < cluster.cells().length; cell++) {
            BlockState member = cluster.cells()[cell];
            if (member == null) {
                continue;
            }
            BakedModel model = patchwork$model(member);
            if (renderType != null && !model.getRenderTypes(member, rand, ModelData.EMPTY).contains(renderType)) {
                continue;
            }
            for (BakedQuad quad : model.getQuads(member, side, rand, ModelData.EMPTY, renderType)) {
                out.add(patchwork$moved(quad, cluster.offsets()[cell], cluster.tints()[cell]));
            }
        }
        return out;
    }

    @Unique
    private static BakedModel patchwork$model(BlockState member) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(member);
    }

    /** A copy of the quad shifted into its cell, with the member's tint baked into the vertex colours. */
    @Unique
    private static BakedQuad patchwork$moved(BakedQuad quad, float[] offset, int[] tints) {
        int[] v = Arrays.copyOf(quad.getVertices(), quad.getVertices().length);
        int stride = v.length / 4;
        int tint = quad.isTinted() && quad.getTintIndex() < PATCHWORK$TINTS ? tints[quad.getTintIndex()] : -1;
        for (int i = 0; i < 4; i++) {
            int b = i * stride;
            v[b] = Float.floatToRawIntBits(Float.intBitsToFloat(v[b]) + offset[0]);
            v[b + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[b + 1]) + offset[1]);
            v[b + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(v[b + 2]) + offset[2]);
            if (tint != -1) {
                v[b + 3] = patchwork$multiplyAbgr(v[b + 3], tint);
            }
        }
        return new BakedQuad(v, quad.isTinted() ? -1 : quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    /** Vertex colour is ABGR; the tint is ARGB. */
    @Unique
    private static int patchwork$multiplyAbgr(int abgr, int argb) {
        int r = (abgr & 0xFF) * (argb >> 16 & 0xFF) / 255;
        int g = (abgr >> 8 & 0xFF) * (argb >> 8 & 0xFF) / 255;
        int b = (abgr >> 16 & 0xFF) * (argb & 0xFF) / 255;
        return (abgr & 0xFF000000) | b << 16 | g << 8 | r;
    }
}
