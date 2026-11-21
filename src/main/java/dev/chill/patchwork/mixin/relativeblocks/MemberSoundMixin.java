package dev.chill.patchwork.mixin.relativeblocks;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty on purpose: it only puts Patchwork's mixin plugin on MultiPlayerGameMode, whose preApply then runs
 * {@link dev.chill.patchwork.relativeblocks.MemberSoundRewrite} before Olaf's Relatively Placed Blocks' own hook is
 * applied there. That lets the unmodified mod load on NeoForge, with each cluster member's own mining sound.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MemberSoundMixin {
}
