package dev.chill.patchwork.devtest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Test-only script, off unless the game runs with -Dpatchwork.test.allblocks=1 (test copies only, flat test world):
 * a crash sweep over every block in the game.
 * 1. Tally: blocks and block states, per mod.
 * 2. State sweep: every block state's shapes, light, render shape and client model are asked for once; anything that
 *    throws is logged, and states whose model is the missing (purple-black) one are counted.
 * 3. Field: daytime frozen, no spawns or fire; every block's default state is placed in its own cell on a grid
 *    (fluids inside a glass ring), a few hundred per tick, with progress logged so a crash names the block.
 *    The cell of each block goes to patchwork-allblocks.csv in the game folder.
 * 4. Tour: the player flies over the grid in rows so every chunk is drawn (and saved and reloaded, which also runs
 *    every block entity through NBT), with a screenshot at each stop. "[Patchwork/test] allblocks done" ends it.
 */
public final class AllBlocksTest {
    public static final boolean ON = System.getProperty("patchwork.test.allblocks") != null;
    private static final Logger LOG = LogUtils.getLogger();
    private static final int SPACING = 3;
    private static final int PER_TICK = 200;
    private static final int TOUR_STEP = 48;
    private static final int TOUR_WAIT = 80;

    private static int ticks;
    private static int phase;
    private static List<BlockState> blocks;
    private static int cols;
    private static BlockPos origin;
    private static volatile int placed;
    private static volatile boolean busy;
    private static int failures;
    private static List<BlockPos> stops;
    private static int stop;
    private static int wait;
    private static PrintWriter csv;

    private AllBlocksTest() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null || phase < 0) {
            return;
        }
        ticks++;
        try {
            switch (phase) {
                case 0 -> {
                    if (ticks >= 100) {
                        mc.options.hideGui = true;
                        tallyAndSweep(mc);
                        BlockPos p = mc.player.blockPosition();
                        origin = new BlockPos(p.getX() + 4, p.getY(), p.getZ() + 4);
                        server.execute(() -> setUp(server));
                        phase = 1;
                    }
                }
                case 1 -> {
                    if (busy) {
                        return;
                    }
                    if (placed >= blocks.size()) {
                        finishField();
                        phase = 2;
                        return;
                    }
                    busy = true;
                    server.execute(() -> placeBatch(server));
                }
                case 2 -> tour(mc, server);
                default -> { }
            }
        } catch (Throwable t) {
            LOG.error("[Patchwork/test] allblocks script failed in phase {}", phase, t);
            phase = -1;
        }
    }

    private static void tallyAndSweep(Minecraft mc) {
        Map<String, int[]> perMod = new TreeMap<>();
        blocks = new ArrayList<>();
        int states = 0, broken = 0, missingModel = 0;
        BakedModel missing = mc.getModelManager().getMissingModel();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            int[] count = perMod.computeIfAbsent(id.getNamespace(), k -> new int[2]);
            count[0]++;
            if (!block.defaultBlockState().isAir()) {
                blocks.add(block.defaultBlockState());
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                count[1]++;
                states++;
                try {
                    state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    state.getRenderShape();
                    state.getFluidState();
                    BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                    if (model == missing && !state.isAir() && state.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
                        missingModel++;
                        if (missingModel <= 200) {
                            LOG.info("[Patchwork/test] allblocks missing model: {}", state);
                        }
                    }
                } catch (Throwable t) {
                    broken++;
                    LOG.warn("[Patchwork/test] allblocks state threw: {}", state, t);
                }
            }
        }
        LOG.info("[Patchwork/test] allblocks tally: {} blocks, {} block states, {} mods", BuiltInRegistries.BLOCK.size(), states, perMod.size());
        perMod.forEach((mod, c) -> LOG.info("[Patchwork/test] allblocks tally {}: {} blocks, {} states", mod, c[0], c[1]));
        LOG.info("[Patchwork/test] allblocks sweep: {} states threw, {} drawn states have the missing model", broken, missingModel);
        cols = (int) Math.ceil(Math.sqrt(blocks.size()));
        try {
            csv = new PrintWriter(Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("patchwork-allblocks.csv")));
            csv.println("index,block,x,y,z,result");
        } catch (IOException e) {
            LOG.warn("[Patchwork/test] allblocks csv not written", e);
        }
    }

    private static void setUp(MinecraftServer server) {
        for (String command : List.of("gamerule doDaylightCycle false", "time set noon", "gamerule doWeatherCycle false",
                "weather clear", "gamerule doMobSpawning false", "gamerule doFireTick false", "gamerule doPatrolSpawning false",
                "gamerule doTraderSpawning false", "gamerule doWardenSpawning false", "kill @e[type=!player]")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }
        LOG.info("[Patchwork/test] allblocks field: {} blocks on a {}x{} grid from {}", blocks.size(), cols, cols, origin);
    }

    private static BlockPos cell(int index) {
        return origin.offset((index % cols) * SPACING, 0, (index / cols) * SPACING);
    }

    private static void placeBatch(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            int end = Math.min(blocks.size(), placed + PER_TICK);
            for (int i = placed; i < end; i++) {
                BlockState state = blocks.get(i);
                BlockPos pos = cell(i);
                String result = "ok";
                try {
                    if (!state.getFluidState().isEmpty()) {
                        for (BlockPos ring : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
                            if (!ring.equals(pos)) {
                                level.setBlock(ring, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            }
                        }
                    }
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                } catch (Throwable t) {
                    failures++;
                    result = "threw " + t;
                    LOG.warn("[Patchwork/test] allblocks place threw: {}", BuiltInRegistries.BLOCK.getKey(state.getBlock()), t);
                }
                if (csv != null) {
                    csv.println(i + "," + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "," + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ",\"" + result.replace('"', '\'') + "\"");
                }
            }
            placed = end;
            if (end % 1000 < PER_TICK || end == blocks.size()) {
                LOG.info("[Patchwork/test] allblocks placed {}/{} (last {})", end, blocks.size(), BuiltInRegistries.BLOCK.getKey(blocks.get(end - 1).getBlock()));
            }
        } finally {
            busy = false;
        }
    }

    private static void finishField() {
        if (csv != null) {
            csv.close();
        }
        LOG.info("[Patchwork/test] allblocks field done: {} placed, {} threw", blocks.size(), failures);
        stops = new ArrayList<>();
        int size = cols * SPACING;
        boolean back = false;
        for (int z = 0; z <= size; z += TOUR_STEP) {
            List<BlockPos> row = new ArrayList<>();
            for (int x = 0; x <= size; x += TOUR_STEP) {
                row.add(origin.offset(x, 24, z));
            }
            if (back) {
                java.util.Collections.reverse(row);
            }
            back = !back;
            stops.addAll(row);
        }
        LOG.info("[Patchwork/test] allblocks tour: {} stops", stops.size());
    }

    private static void tour(Minecraft mc, MinecraftServer server) {
        if (wait > 0) {
            if (--wait == 0) {
                int shot = stop;
                Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                        message -> LOG.info("[Patchwork/test] allblocks shot {}: {}", shot, message.getString()));
                stop++;
            }
            return;
        }
        if (stop >= stops.size()) {
            LOG.info("[Patchwork/test] allblocks done: {} blocks, {} threw while placed, tour of {} stops finished", blocks.size(), failures, stops.size());
            phase = -1;
            return;
        }
        BlockPos p = stops.get(stop);
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "tp @a " + p.getX() + " " + p.getY() + " " + p.getZ() + " -45 50"));
        wait = TOUR_WAIT;
    }
}
