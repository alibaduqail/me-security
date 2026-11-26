package dev.ae2security.client;

import java.util.Locale;

import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AECheckbox;
import dev.ae2security.security.Permission;
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

    static String key(Permission permission) {
        return "permission.ae2security." + permission.name().toLowerCase(Locale.ROOT);
    }

    static Icon icon(Permission permission) {
        return switch (permission) {
            case VIEW -> Icon.VIEW_MODE_STORED;
            case INSERT -> Icon.ACCESS_WRITE;
            case EXTRACT -> Icon.ACCESS_READ;
            case CRAFT -> Icon.CRAFT_HAMMER;
            case COLLECT -> Icon.AUTO_EXPORT_ON;
            case BUILD -> Icon.PLACEMENT_ITEM;
        };
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
