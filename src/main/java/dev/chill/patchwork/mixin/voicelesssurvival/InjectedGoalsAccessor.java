package dev.chill.patchwork.mixin.voicelesssurvival;

import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The goals Voiceless Survival injected into one mob ({@code MobGoalInjector$InjectedGoals} is private). */
@Mixin(targets = "com.armilp.ezvcsurvival.events.MobGoalInjector$InjectedGoals", remap = false)
public interface InjectedGoalsAccessor {
    @Accessor("followGoal")
    Goal patchwork$followGoal();

    @Accessor("runawayGoal")
    Goal patchwork$runawayGoal();

    @Accessor("generalSoundGoal")
    Goal patchwork$generalSoundGoal();
}
