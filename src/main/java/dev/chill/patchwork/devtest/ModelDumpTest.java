package dev.chill.patchwork.devtest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.List;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.modeldump=1 (test copies only): once the player is
 * in the world, bakes a few block-entity model layers the way block-entity baking mods do and logs each part's pose,
 * scale, visibility, cube count and the overall size of its geometry.
 */
public final class ModelDumpTest {
    public static final boolean ON = System.getProperty("patchwork.test.modeldump") != null;
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean done;
    private static int ticks;

    private ModelDumpTest() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (done || mc.player == null) {
            return;
        }
        if (++ticks < 200) {
            return; // let the test world's chest get meshed first
        }
        done = true;
        dumpObe();
        for (ModelLayerLocation layer : List.of(ModelLayers.CHEST, ModelLayers.SHULKER, ModelLayers.BED_HEAD)) {
            try {
                ModelPart root = mc.getEntityModels().bakeLayer(layer);
                LOG.info("[Patchwork/test] modeldump {} root={} {}", layer, root.getClass().getName(), pose(root));
                root.getAllParts().forEach(p -> LOG.info("[Patchwork/test] modeldump   part {} {}", p.getClass().getSimpleName(), pose(p)));
                Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
                int[] polys = {0};
                root.visit(new PoseStack(), (pose, path, index, cube) -> {
                    polys[0]++;
                    for (float x : new float[] {cube.minX, cube.maxX}) {
                        for (float y : new float[] {cube.minY, cube.maxY}) {
                            for (float z : new float[] {cube.minZ, cube.maxZ}) {
                                Vector3f w = pose.pose().transformPosition(x / 16f, y / 16f, z / 16f, new Vector3f());
                                min.min(w);
                                max.max(w);
                            }
                        }
                    }
                });
                LOG.info("[Patchwork/test] modeldump {} cubes={} min={} max={}", layer, polys[0], min, max);
            } catch (Throwable t) {
                LOG.warn("[Patchwork/test] modeldump {} failed", layer, t);
            }
        }
    }

    /** OBE's baked chest model, if OBE is loaded: each quad's direction and its corner positions. */
    private static void dumpObe() {
        try {
            var util = Class.forName("fr.madu59.obe.client.resources.ResourceUtil");
            var getModel = util.getMethod("getModel", net.minecraft.world.level.block.state.BlockState.class);
            var state = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();
            Object model = getModel.invoke(null, state);
            if (model == null) {
                LOG.info("[Patchwork/test] modeldump obe chest: not cached yet");
                return;
            }
            var quads = ((net.minecraft.client.resources.model.BakedModel) model).getQuads(state, null, net.minecraft.util.RandomSource.create(), net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            LOG.info("[Patchwork/test] modeldump obe chest {} quads={}", model.getClass().getSimpleName(), quads.size());
            for (var q : quads) {
                int[] v = q.getVertices();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    int o = i * 8;
                    sb.append(String.format(" (%.3f,%.3f,%.3f)", Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2])));
                }
                LOG.info("[Patchwork/test] modeldump obe quad {}{}", q.getDirection(), sb);
            }
        } catch (ClassNotFoundException e) {
            LOG.info("[Patchwork/test] modeldump obe not loaded");
        } catch (Throwable t) {
            LOG.warn("[Patchwork/test] modeldump obe failed", t);
        }
    }

    private static String pose(ModelPart p) {
        return String.format("visible=%s skip=%s xyz=(%.2f,%.2f,%.2f) rot=(%.2f,%.2f,%.2f) scale=(%.2f,%.2f,%.2f) cubes=%s",
                p.visible, p.skipDraw, p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale,
                p.isEmpty() ? "0" : "some");
    }
}
