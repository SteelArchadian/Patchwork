package dev.chill.patchwork.mixin.modlistrecent;

import dev.chill.patchwork.modlistrecent.RecentMods;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Adds a "Recent" button next to the mod list's Off / A-Z / Z-A sort buttons: the mods you last clicked or opened the
 * config of come first (kept across restarts, see {@link RecentMods}), then the rest A-Z. The four buttons share the
 * row the three had.
 */
@Mixin(value = ModListScreen.class, remap = false)
public abstract class RecentSortMixin extends Screen {
    @Unique private static final Set<String> SORT_KEYS = Set.of("fml.menu.mods.normal", "fml.menu.mods.a_to_z", "fml.menu.mods.z_to_a");

    @Shadow private boolean sorted;
    @Shadow private ModListWidget.ModEntry selected;

    @Unique private boolean patchwork$recent;
    @Unique private Button patchwork$recentButton;
    @Unique private final List<Button> patchwork$sortButtons = new ArrayList<>();

    protected RecentSortMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void patchwork$addRecent(CallbackInfo ci) {
        patchwork$sortButtons.clear();
        for (GuiEventListener child : children()) {
            if (child instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t && SORT_KEYS.contains(t.getKey())) {
                patchwork$sortButtons.add(b);
            }
        }
        if (patchwork$sortButtons.size() != 3) {
            return;
        }
        patchwork$sortButtons.sort(Comparator.comparingInt(Button::getX));
        Button first = patchwork$sortButtons.get(0), last = patchwork$sortButtons.get(2);
        int left = first.getX(), right = last.getX() + last.getWidth();
        int gap = patchwork$sortButtons.get(1).getX() - (first.getX() + first.getWidth());
        int width = (right - left - 3 * gap) / 4;
        for (int i = 0; i < 3; i++) {
            Button b = patchwork$sortButtons.get(i);
            b.setX(left + i * (width + gap));
            b.setWidth(width);
        }
        patchwork$recentButton = addRenderableWidget(Button.builder(Component.translatable("patchwork_fixes.modlist.recent"), b -> {
                    patchwork$recent = true;
                    sorted = false;
                    patchwork$sortButtons.forEach(s -> s.active = true);
                    b.active = false;
                })
                .bounds(left + 3 * (width + gap), first.getY(), right - (left + 3 * (width + gap)), first.getHeight())
                .tooltip(Tooltip.create(Component.translatable("patchwork_fixes.modlist.recent.tooltip")))
                .build());
        patchwork$recentButton.active = !patchwork$recent;
        if (patchwork$recent) {
            patchwork$sortButtons.forEach(s -> s.active = true);
        }
    }

    /** Any of NeoForge's own sorts ends the Recent sort. */
    @Inject(method = "resortMods", at = @At("HEAD"), require = 1)
    private void patchwork$endRecent(CallbackInfo ci) {
        patchwork$recent = false;
        if (patchwork$recentButton != null) {
            patchwork$recentButton.active = true;
        }
    }

    /** Opening a mod's config counts as using it (keyboard users can get there without clicking the mod). */
    @Inject(method = "displayModConfig", at = @At("HEAD"), require = 1)
    private void patchwork$configured(CallbackInfo ci) {
        if (selected != null) {
            RecentMods.touch(selected.getInfo().getModId());
        }
    }

    @Redirect(method = "tick", require = 1,
            at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
    private void patchwork$sort(List<ModContainer> mods, Comparator<ModContainer> sortType) {
        if (!patchwork$recent) {
            mods.sort(sortType);
            return;
        }
        mods.sort(Comparator.comparingLong((ModContainer m) -> -RecentMods.lastUsed(m.getModId()))
                .thenComparing(m -> m.getModInfo().getDisplayName(), String.CASE_INSENSITIVE_ORDER));
    }
}
