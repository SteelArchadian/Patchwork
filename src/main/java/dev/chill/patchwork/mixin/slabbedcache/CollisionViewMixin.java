package dev.chill.patchwork.mixin.slabbedcache;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.chill.patchwork.slabbedcache.OffsetCache;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets Slabbed offsets asked through an entity's collision view of the level be kept too (see OffsetCache). */
@Mixin(BlockCollisions.class)
public abstract class CollisionViewMixin<T> {
    @Shadow @Final private CollisionGetter collisionGetter;
    @Shadow @Final private CollisionContext context;

    @WrapMethod(method = "computeNext")
    private T patchwork$offsetScope(Operation<T> original) {
        Object previous = OffsetCache.enterCollisions(collisionGetter, context);
        try {
            return original.call();
        } finally {
            OffsetCache.exitCollisions(previous);
        }
    }
}
