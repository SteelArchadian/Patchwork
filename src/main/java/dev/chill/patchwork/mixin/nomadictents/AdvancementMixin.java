package dev.chill.patchwork.mixin.nomadictents;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.DataResult;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Nomadic Tents 21.1.0 ships recipe-unlock advancements whose criteria include {@code has_ingredient_0..N} that the
 * {@code requirements} list never mentions. 1.21 rejects those, so 137 of its 178 recipes are never unlocked in the
 * recipe book and every data load logs one error per file. Only for advancements that reward nothing but Nomadic Tents
 * recipes, the missing criteria join the first requirement group (the one with {@code has_the_recipe}), which is what
 * the data generator meant: any ingredient, or already knowing the recipe, unlocks it.
 */
@Mixin(Advancement.class)
public abstract class AdvancementMixin {

    @WrapMethod(method = "validate(Lnet/minecraft/advancements/Advancement;)Lcom/mojang/serialization/DataResult;")
    private static DataResult<Advancement> patchwork$completeRequirements(Advancement advancement, Operation<DataResult<Advancement>> original) {
        DataResult<Advancement> result = original.call(advancement);
        if (result.isSuccess() || !rewardsOnlyTentRecipes(advancement)) {
            return result;
        }
        List<List<String>> groups = advancement.requirements().requirements();
        Set<String> listed = new LinkedHashSet<>();
        groups.forEach(listed::addAll);
        if (groups.isEmpty() || !advancement.criteria().keySet().containsAll(listed)) {
            return result; // a requirement names a criterion that doesn't exist; not the case we know how to fix
        }
        List<String> first = new ArrayList<>(groups.get(0));
        for (String criterion : advancement.criteria().keySet()) {
            if (!listed.contains(criterion)) {
                first.add(criterion);
            }
        }
        List<List<String>> fixed = new ArrayList<>(groups);
        fixed.set(0, List.copyOf(first));
        return original.call(new Advancement(advancement.parent(), advancement.display(), advancement.rewards(),
                advancement.criteria(), new AdvancementRequirements(List.copyOf(fixed)),
                advancement.sendsTelemetryEvent(), advancement.name()));
    }

    private static boolean rewardsOnlyTentRecipes(Advancement advancement) {
        List<ResourceLocation> recipes = advancement.rewards().recipes();
        return !recipes.isEmpty() && recipes.stream().allMatch(id -> "nomadictents".equals(id.getNamespace()));
    }
}
