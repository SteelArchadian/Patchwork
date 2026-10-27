package dev.chill.patchwork.mixin.voicelesssurvival;

import com.armilp.ezvcsurvival.events.MobGoalInjector;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads Voiceless Survival's two static per-mob tables so entries can be dropped when their mob leaves. */
@Mixin(value = MobGoalInjector.class, remap = false)
public interface MobGoalInjectorAccessor {
    @Accessor("TRACKED_MOBS")
    static Set<Mob> patchwork$trackedMobs() {
        throw new AssertionError();
    }

    @Accessor("INJECTED_GOALS")
    static Map<UUID, Object> patchwork$injectedGoals() {
        throw new AssertionError();
    }
}
