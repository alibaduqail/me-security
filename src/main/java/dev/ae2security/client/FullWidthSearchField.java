package dev.ae2security.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** An AE2 text field that fills AE2's 128px texture past its width, so it can span the player list. */
final class FullWidthSearchField extends AETextField {
    private static final int TEXTURE_SIZE = 128;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/text_field.png");

    FullWidthSearchField(ScreenStyle style, Font font, int x, int y, int width, int height) {
        super(style, font, x, y, width, height);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            var area = getTooltipArea();
            int extension = area.getWidth() - TEXTURE_SIZE;
            if (extension > 0) graphics.blit(TEXTURE, area.getX() + 127, area.getY(), extension, 12,
                    1, isFocused() ? 24 : 0, 1, 12, TEXTURE_SIZE, TEXTURE_SIZE);
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
