package dev.ae2security.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The left-toolbar tab that opens the ownership transfer popup. AE2's {@link IconButton} only
 * fades its icon in half-size mode, so this draws the full-size button itself to show the
 * disabled state: without a trusted player selected there is nobody to hand ownership to.
 */
final class TransferTabButton extends IconButton {
    TransferTabButton(OnPress onPress) {
        super(onPress);
    }

    @Override
    protected Icon getIcon() {
        return Icon.ARROW_RIGHT;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        boolean hovered = active && isHovered();
        int yOffset = hovered ? 1 : 0;
        var background = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter().dest(getX() - 1, getY() + yOffset, 18, 20).zOffset(2).blit(graphics);
        getIcon().getBlitter().opacity(active ? 1f : 0.35f)
                .dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(graphics);
    }
}
