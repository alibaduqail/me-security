package dev.ae2security.client;

import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import dev.ae2security.network.PolicySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * One player in the list, drawn like a row of AE2's crafting CPU list: name on top and an
 * info line below. Trusted players show an open lock and one access LED per permission;
 * everyone else a dimmed closed lock.
 */
final class PlayerRowButton extends Button {
    static final int HEIGHT = 24;
    private static final int TEXT_LEFT = 22;

    private final ScreenStyle style;
    private PolicySnapshot.PlayerEntry player;
    private boolean selected;

    PlayerRowButton(ScreenStyle style, int x, int y, int width, OnPress onPress) {
        super(x, y, width, HEIGHT, Component.empty(), onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    void show(PolicySnapshot.PlayerEntry player, boolean selected) {
        this.player = player;
        this.selected = selected;
        setMessage(Component.literal(player.name()));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (player == null) return;
        int x = getX();
        int y = getY();
        var font = Minecraft.getInstance().font;
        SecurityGui.nineSlice(graphics, style.getImage(selected ? "rowSelected" : "row"), 1, 1, 1, 2,
                x, y, width, height);
        if (!selected && isHoveredOrFocused()) {
            // AE2's selection blue as a 1px inner outline, so hover never reads as a selection.
            int hover = SecurityGui.color(style, PaletteColor.SELECTION_COLOR);
            graphics.renderOutline(x + 1, y + 1, width - 2, height - 3, hover);
        }

        boolean trusted = player.trusted();
        int textColor = SecurityGui.color(style, PaletteColor.DEFAULT_TEXT_COLOR);
        var icon = (trusted ? Icon.UNLOCKED : Icon.LOCKED).getBlitter();
        // AE2's white glyphs vanish on the light selection blue, so draw them in the text color there.
        if (selected) icon.colorRgb(textColor & 0xFFFFFF);
        if (!trusted) icon.opacity(0.55f);
        icon.dest(x + 3, y + 4).blit(graphics);

        // Names are at most 16 characters, so the widest still fits; clip defensively anyway.
        var name = font.plainSubstrByWidth(player.name(), width - TEXT_LEFT - 2);
        if (trusted) {
            SecurityGui.text(graphics, font, name, x + TEXT_LEFT, y + 4, textColor);
            SecurityGui.accessLeds(graphics, style, x + TEXT_LEFT, y + 15, player.permissions());
        } else {
            SecurityGui.text(graphics, font, name, x + TEXT_LEFT, y + 8, textColor);
        }
    }
}
