package dev.chill.patchwork.mixin.carryonrender;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Carry On drew carried blocks and mobs (on every player, and on your own with First-person Model) after particles,
 * once a shader pack has already lit the world, so packs like Bliss showed them see-through or not at all. They are
 * now drawn with the block entities, where the pack lights them like any other model. See {@link CarryBufferMixin}.
 */
@Mixin(targets = "tschipp.carryon.events.ClientEvents", remap = false)
public abstract class CarryStageMixin {
    @Redirect(method = "onRenderLevel", require = 1,
            at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
                    target = "Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;AFTER_PARTICLES:Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;"))
    private static RenderLevelStageEvent.Stage patchwork$withBlockEntities() {
        return RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
    }
}
