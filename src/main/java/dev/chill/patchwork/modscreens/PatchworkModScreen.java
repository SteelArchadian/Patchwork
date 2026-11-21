package dev.chill.patchwork.modscreens;

import dev.chill.patchwork.Module;
import dev.chill.patchwork.PatchworkSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Patchwork's switches for one mod, opened from that mod's own settings (see {@link ModSettingsInjector}). Same
 * patchwork.toml keys as Mods → Patchwork → Config, so both always show the same; changes apply after a restart.
 */
public final class PatchworkModScreen extends Screen {
    private static final int ROW_H = 24, ROW_W = 300;
    private final Screen parent;
    private final String modId;
    private boolean changed;

    PatchworkModScreen(Screen parent, String modId, String modName) {
        super(Component.translatable("patchwork_fixes.modscreen.title", modName));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        List<Module> modules = ModSettingsInjector.modulesFor(modId);
        int x = (width - ROW_W) / 2;
        int y = Math.max(32, (height - modules.size() * ROW_H) / 2 - 20);
        for (Module module : modules) {
            var toggle = PatchworkSpec.TOGGLES.get(module);
            Component tooltip = Component.translatable("patchwork_fixes.configuration." + module.key + ".tooltip")
                    .append("\n\n").append(status(module));
            addRenderableWidget(Checkbox.builder(Component.translatable("patchwork_fixes.configuration." + module.key), font)
                    .pos(x, y)
                    .maxWidth(ROW_W)
                    .selected(toggle.get())
                    .tooltip(Tooltip.create(tooltip))
                    .onValueChange((box, on) -> {
                        toggle.set(on);
                        changed = true;
                    })
                    .build());
            y += ROW_H;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    /** Whether the module is running now; a switch only takes effect after a restart. */
    private static Component status(Module module) {
        if (module.active()) {
            return Component.translatable("patchwork_fixes.modscreen.running").withStyle(ChatFormatting.GREEN);
        }
        if (!module.enabled()) {
            return Component.translatable("patchwork_fixes.modscreen.off").withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("patchwork_fixes.modscreen.version", Module.modVersion(module.targetModId),
                String.join(", ", module.targetVersions)).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(changed ? "patchwork_fixes.modscreen.restart"
                : "patchwork_fixes.modscreen.hint"), width / 2, height - 42, changed ? 0xFFFF55 : 0xA0A0A0);
    }

    @Override
    public void onClose() {
        if (changed) {
            PatchworkSpec.SPEC.save();
        }
        minecraft.setScreen(parent);
    }
}
