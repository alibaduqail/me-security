package dev.ae2security.client;

import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AECheckbox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A permission row: the permission's AE2 icon in a slot, its name, and AE2's on/off switch.
 * The switch only shows what the server holds; a click asks for a change instead of flipping
 * locally, so a slow round trip never shows a state the server has not confirmed.
 */
final class PermissionSwitch extends AECheckbox {
    static final int HEIGHT = 18;
    private static final int SWITCH_WIDTH = 22;

    private final ScreenStyle style;
    private final Icon icon;
    private final Runnable onToggle;

    PermissionSwitch(ScreenStyle style, Icon icon, Component name, int x, int y, int width, Runnable onToggle) {
        super(x, y, width, HEIGHT, style, name);
        this.style = style;
        this.icon = icon;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress() {
        onToggle.run();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean on = isSelected();
        boolean hovered = active && isHoveredOrFocused();

        SecurityGui.iconSlot(graphics, icon, x, y, on ? 1f : 0.4f);

        var font = Minecraft.getInstance().font;
        int textColor = SecurityGui.color(style, active ? PaletteColor.DEFAULT_TEXT_COLOR : PaletteColor.MUTED_TEXT_COLOR);
        int labelWidth = width - 22 - SWITCH_WIDTH - 4;
        SecurityGui.text(graphics, font, font.plainSubstrByWidth(getMessage().getString(), labelWidth),
                x + 22, y + 5, textColor);

        String image = on ? (hovered ? "switchOnHover" : "switchOn") : (hovered ? "switchOffHover" : "switchOff");
        style.getImage(image).copy().opacity(active ? 1f : 0.5f)
                .dest(x + width - SWITCH_WIDTH, y + 3).blit(graphics);
    }
}
