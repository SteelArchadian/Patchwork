package dev.chill.patchwork.mixin.seamlessloadingscreen;

import com.minenash.seamless_loading_screen.ScreenshotLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.WorldStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Seamless Loading Screen 2.2.1 learns the world's screenshot file only from its own world-list, quick-play and
 * create-world hooks. A world opened any other way (an auto-start or automation mod) leaves the name unset (or set
 * to the previous world), and leaving the world then crashes in OnLeaveHelper.takeScreenShot. Every singleplayer
 * world goes through doWorldLoad, so the name is set there, the same way the mod's world-list hook sets it.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "doWorldLoad", at = @At("TAIL"), require = 1)
    private void patchwork$nameScreenshot(LevelStorageSource.LevelStorageAccess levelStorage, PackRepository packRepository,
                                          WorldStem worldStem, boolean newWorld, CallbackInfo ci) {
        ScreenshotLoader.setScreenshot(levelStorage.getLevelId());
    }
}
