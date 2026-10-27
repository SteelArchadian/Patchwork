package dev.chill.patchwork.voicelesssurvival;

import dev.chill.patchwork.Module;
import dev.chill.patchwork.mixin.voicelesssurvival.InjectedGoalsAccessor;
import dev.chill.patchwork.mixin.voicelesssurvival.MobGoalInjectorAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.Map;
import java.util.Set;

/**
 * Voiceless Survival keeps a mob → injected-goals table keyed weakly by the mob's UUID, but every stored value holds
 * goals that hold the mob, which holds that UUID, so no entry (and no mob) is ever released. When a mob leaves a
 * server level (death, despawn, chunk unload, dimension change) this drops its entries. A returning mob is a new
 * object whose selector lacks the old goals, so Voiceless Survival's own join handler re-injects them as it always did.
 */
public final class MobLeakFix {
    private MobLeakFix() {}

    public static void register() {
        if (Module.VOICELESS_SURVIVAL.active()) {
            NeoForge.EVENT_BUS.register(new MobLeakFix());
        }
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        Set<Mob> tracked = MobGoalInjectorAccessor.patchwork$trackedMobs();
        Map<java.util.UUID, Object> injected = MobGoalInjectorAccessor.patchwork$injectedGoals();
        // Same thread as Voiceless Survival's own join handler (the server level's tick thread).
        tracked.remove(mob);
        Object goals = injected.get(mob.getUUID());
        // Only drop the entry if it belongs to this mob object (a re-created mob with the same UUID, e.g. after a
        // dimension change, may already have joined and registered its own goals).
        if (goals instanceof InjectedGoalsAccessor accessor && belongsTo(mob, accessor)) {
            injected.remove(mob.getUUID());
        }
    }

    private static boolean belongsTo(Mob mob, InjectedGoalsAccessor goals) {
        Set<WrappedGoal> available = mob.goalSelector.getAvailableGoals();
        return contains(available, goals.patchwork$followGoal())
                || contains(available, goals.patchwork$runawayGoal())
                || contains(available, goals.patchwork$generalSoundGoal());
    }

    private static boolean contains(Set<WrappedGoal> available, Goal goal) {
        if (goal == null) {
            return false;
        }
        for (WrappedGoal wrapped : available) {
            if (wrapped.getGoal() == goal) {
                return true;
            }
        }
        return false;
    }
}
