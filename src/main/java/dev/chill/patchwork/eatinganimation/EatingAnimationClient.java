package dev.chill.patchwork.eatinganimation;

import dev.chill.patchwork.Module;
import dev.chill.patchwork.Patchwork;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforge.resource.EmptyPackResources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Eating Animation by Matyrobbrt (MIT), ported from Groovy to Java so the pack no longer needs Groovy ModLoader.
 * Same item properties ({@code eatinganimation:eat/eating/drink/drinking}), the same vanilla food models (served as
 * an always-on built-in pack) and the same generated compat packs for other mods' foods. See LICENSE-EatingAnimation.txt.
 */
public final class EatingAnimationClient {
    private static final String NAMESPACE = "eatinganimation";
    /** Shared animation clock for other players, who don't send use-progress (as in the original). */
    private static float animationTicks;

    private EatingAnimationClient() {}

    public static void init(IEventBus modBus) {
        if (!Module.EATING_ANIMATION.active()) {
            return;
        }
        modBus.addListener(EatingAnimationClient::onClientSetup);
        modBus.addListener(EatingAnimationClient::onAddPackFinders);
        NeoForge.EVENT_BUS.addListener(EatingAnimationClient::onPlayerTick);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.registerGeneric(id("eat"), EatingAnimationClient::useProgress);
            ItemProperties.registerGeneric(id("eating"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack && stack.getFoodProperties(entity) != null ? 1F : 0F);
            ItemProperties.registerGeneric(id("drink"), (stack, level, entity, seed) ->
                    entity == null || entity.getUseItem() != stack ? 0F
                            : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 30F);
            ItemProperties.registerGeneric(id("drinking"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                            && stack.getItem().getUseAnimation(stack) == UseAnim.DRINK ? 1F : 0F);
        });
    }

    private static float useProgress(ItemStack stack, Object level, LivingEntity entity, int seed) {
        if (entity == null) {
            return 0F;
        }
        if (entity instanceof RemotePlayer && entity.getTicksUsingItem() > 31) {
            return animationTicks / 30F;
        }
        return entity.getUseItem() != stack ? 0F : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 30F;
    }

    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof RemotePlayer player && player.getTicksUsingItem() > 31) {
            animationTicks = animationTicks < 31 ? animationTicks + 1 : 0;
        }
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(Patchwork.MOD_ID, "resourcepacks/eatinganimation"),
                PackType.CLIENT_RESOURCES, Component.literal("Eating Animation"), PackSource.BUILT_IN, true, Pack.Position.TOP);

        IModFile file = ModList.get().getModFileById(Patchwork.MOD_ID).getFile();
        Path compatRoot = file.findResource("eatinganimation_compat");
        List<Pack> children = new ArrayList<>();
        try (Stream<Path> mods = Files.list(compatRoot)) {
            for (Path modFolder : mods.filter(Files::isDirectory).toList()) {
                String modId = modFolder.getFileName().toString().replace("/", "");
                if (!ModList.get().isLoaded(modId)) {
                    continue;
                }
                PackLocationInfo location = new PackLocationInfo("eating_animations_compat_" + modId,
                        Component.literal("eating animations compat for " + modId), PackSource.BUILT_IN, Optional.empty());
                PackResources resources = new ModCompatPack(location, modFolder, modId);
                Pack pack = Pack.readMetaAndCreate(location, new Pack.ResourcesSupplier() {
                    @Override
                    public PackResources openPrimary(PackLocationInfo info) {
                        return resources;
                    }

                    @Override
                    public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                        return resources;
                    }
                }, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack != null) {
                    children.add(pack);
                }
            }
        } catch (IOException e) {
            Patchwork.LOG.error("[Patchwork/eatinganimation] Could not list compat data", e);
        }

        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        PackLocationInfo parent = new PackLocationInfo("eatinganimations_compat", Component.literal("Eating Animations Compat"),
                PackSource.DEFAULT, Optional.empty());
        Pack compat = Pack.readMetaAndCreate(parent,
                new EmptyPackResources.EmptyResourcesSupplier(new PackMetadataSection(
                        Component.translatable("eatinganimations.resources.compat.desc", children.size()), format)),
                PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (compat != null) {
            Pack withChildren = compat.withChildren(children);
            event.addRepositorySource(consumer -> consumer.accept(withChildren));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }
}
