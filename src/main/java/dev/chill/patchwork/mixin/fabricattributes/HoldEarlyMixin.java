package dev.chill.patchwork.mixin.fabricattributes;

import dev.chill.patchwork.fabricattributes.EarlyAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds Fabric mob attribute registrations made before NeoForge's registries exist; see {@link EarlyAttributes}. */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry", remap = false)
public abstract class HoldEarlyMixin {
    @Inject(method = "register(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void patchwork$holdEarly(EntityType<? extends LivingEntity> type, AttributeSupplier supplier, CallbackInfo ci) {
        if (EarlyAttributes.hold(type, supplier)) {
            ci.cancel();
        }
    }
}
