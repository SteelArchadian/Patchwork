package dev.chill.patchwork.mixin.c2meticking;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Pseudo
@Mixin(targets = "com.ishland.flowsched.scheduler.ItemHolder", remap = false)
public interface ItemHolderAccessor {
    @Accessor(value = "futures", remap = false)
    CompletableFuture<Void>[] patchwork$futures();
}
