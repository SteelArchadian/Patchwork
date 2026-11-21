package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.slf4j.Logger;

/**
 * Test-only, off unless -Dpatchwork.test.arclose=1: in a world, makes and closes a second LevelRenderer three times
 * (what a portal mod's far-side view does), drawing the player in third person in between. Without the fix Accelerated
 * Rendering frees its shared buffers and shaders on each close (GL errors, then a native crash).
 */
public final class ARCloseTest {
    public static final boolean ON = System.getProperty("patchwork.test.arclose") != null;
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;
    private static int rounds;

    private ARCloseTest() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || rounds > 3) return;
        ticks++;
        if (ticks == 100) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (ticks >= 200 && ticks % 200 == 0) {
            if (rounds == 3) {
                LOG.info("[Patchwork/test] arclose done; programs loaded={}", programsLoaded());
                rounds++;
                return;
            }
            LevelRenderer extra = new LevelRenderer(mc, mc.getEntityRenderDispatcher(), mc.getBlockEntityRenderDispatcher(), mc.renderBuffers());
            extra.onResourceManagerReload(mc.getResourceManager());
            extra.close();
            rounds++;
            LOG.info("[Patchwork/test] arclose round {}: closed a second LevelRenderer; programs loaded={}", rounds, programsLoaded());
        }
    }

    private static Object programsLoaded() {
        try {
            return Class.forName("com.github.argon4w.acceleratedrendering.core.programs.ComputeShaderProgramLoader")
                    .getMethod("isProgramsLoaded").invoke(null);
        } catch (Throwable t) {
            return "n/a (" + t.getClass().getSimpleName() + ")";
        }
    }
}
