package dev.chill.patchwork.mixin.obeemf;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chill.patchwork.obeemf.EmfCustomBlocks;
import fr.madu59.obe.client.model.BlockEntityStateModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;

/**
 * Optimized Block Entities bakes chests, shulker boxes and the like into chunk geometry by walking the entity model's
 * cubes. When Entity Model Features has a custom model for one, its cubes only land in the right place while EMF draws
 * them, so the baked copy came out half a block off and sunk into the floor (a chest showed as a flat square on the
 * ground). Those blocks get an empty baked model here and are marked, and {@link EmfBlacklistMixin} then hands them
 * back to their normal renderer, which EMF draws correctly. Blocks without an EMF custom model are baked as before.
 */
@Mixin(value = BlockEntityStateModel.class, remap = false)
public abstract class EmfPartQuadsMixin {
    @Redirect(method = "generateModel", require = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/EntityModelSet;bakeLayer(Lnet/minecraft/client/model/geom/ModelLayerLocation;)Lnet/minecraft/client/model/geom/ModelPart;"))
    private ModelPart patchwork$checkEmfCustom(EntityModelSet models, ModelLayerLocation layer) {
        ModelPart root = models.bakeLayer(layer);
        EmfCustomBlocks.BAKING_CUSTOM.set(EmfCustomBlocks.hasCustomGeometry(root));
        return root;
    }

    @Inject(method = "generateModel", at = @At("RETURN"), cancellable = true, require = 1)
    private void patchwork$leaveEmfCustomToRenderer(ModelLayerLocation layer, TextureAtlasSprite sprite, PoseStack poseStack, boolean useAo,
                                                    BlockState state, CallbackInfoReturnable<SimpleBakedModel> cir) {
        boolean custom = Boolean.TRUE.equals(EmfCustomBlocks.BAKING_CUSTOM.get());
        EmfCustomBlocks.BAKING_CUSTOM.remove();
        if (custom && state != null) {
            EmfCustomBlocks.add(state.getBlock());
            cir.setReturnValue(new SimpleBakedModel(List.of(), new HashMap<>(), useAo, true, false, sprite, null, null));
        }
    }
}
