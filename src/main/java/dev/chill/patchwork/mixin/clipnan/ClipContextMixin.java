package dev.chill.patchwork.mixin.clipnan;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A ray with an infinite (or absurdly far) end never finishes in BlockGetter.traverseBlocks: it steps by 1/length,
 * which is zero, so one bad view vector froze the game: Touch n Motion casts a short ray from every rendered player's eyes to
 * find signs. Such a ray is made zero-length here, which every traversal returns from at once with "nothing hit".
 */
@Mixin(ClipContext.class)
public abstract class ClipContextMixin {
    @Unique private static final Logger LOG = LogUtils.getLogger();
    @Unique private static final Vec3 NOWHERE = new Vec3(0, -10000, 0);
    @Unique private static final double LIMIT = 3.0E7;
    @Unique private static boolean logged;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Vec3 patchwork$finiteFrom(Vec3 from) {
        return finite(from) ? from : nowhere(from);
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static Vec3 patchwork$finiteTo(Vec3 to, Vec3 from) {
        // the first handler has already run, so a bad start is NOWHERE by now
        return from == NOWHERE ? NOWHERE : finite(to) ? to : nowhere(to, from);
    }

    @Unique
    private static boolean finite(Vec3 v) {
        // beyond the world border (30 million) counts as not real too: vanilla's traversal steps by 1/length, so a
        // huge or infinite length means steps of zero and a loop that never ends
        return Math.abs(v.x) < LIMIT && Math.abs(v.y) < LIMIT && Math.abs(v.z) < LIMIT;
    }

    @Unique
    private static Vec3 nowhere(Vec3 bad) {
        return nowhere(bad, NOWHERE);
    }

    @Unique
    private static Vec3 nowhere(Vec3 bad, Vec3 instead) {
        if (!logged) {
            logged = true;
            LOG.warn("[Patchwork] clipnan: a ray to {} was not a real position; it is skipped (logged once)", bad,
                    new Throwable("ray made here"));
        }
        return instead;
    }
}
