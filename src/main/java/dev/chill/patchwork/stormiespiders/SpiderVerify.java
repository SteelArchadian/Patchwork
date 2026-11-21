package dev.chill.patchwork.stormiespiders;

import dev.chill.patchwork.Patchwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test aid, off unless started with -Dpatchwork.verify.stormiespiders=true: every shared-scan answer is compared with
 * Stormie's own lookup, and a few spiders are summoned next to the first player so there is something to compare.
 */
public final class SpiderVerify {
    public static final boolean ON = Boolean.getBoolean("patchwork.verify.stormiespiders");
    private static final AtomicLong CALLS = new AtomicLong();
    private static final AtomicLong MISMATCHES = new AtomicLong();
    private static boolean summoned;

    private SpiderVerify() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> {
            if (!summoned && e.getEntity().level() instanceof ServerLevel level && e.getEntity().tickCount > 100) {
                summoned = true;
                for (int i = 0; i < 24; i++) {
                    var spider = EntityType.SPIDER.create(level);
                    if (spider != null) {
                        double a = i * 0.26;
                        spider.moveTo(e.getEntity().getX() + Math.cos(a) * (3 + i % 6), e.getEntity().getY(),
                                e.getEntity().getZ() + Math.sin(a) * (3 + i % 6), 0, 0);
                        spider.setPersistenceRequired();
                        level.addFreshEntity(spider);
                    }
                }
                Patchwork.LOG.info("[Patchwork] stormiespiders verify: summoned 24 spiders");
            }
        });
    }

    public static void check(List<AABB> ours, List<AABB> theirs) {
        long calls = CALLS.incrementAndGet();
        if (!ours.equals(theirs)) {
            long n = MISMATCHES.incrementAndGet();
            if (n <= 5) {
                Patchwork.LOG.warn("[Patchwork] stormiespiders verify: MISMATCH ours={} theirs={}", ours, theirs);
            }
        }
        if (calls % 20000 == 0) {
            Patchwork.LOG.info("[Patchwork] stormiespiders verify: {} lookups compared, {} mismatches", calls, MISMATCHES.get());
        }
    }
}
