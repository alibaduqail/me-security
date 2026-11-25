package dev.ae2security.client;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import dev.ae2security.security.Permission;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** Drawing helpers that build the Security screen from AE2's own textures, icons and palette. */
final class SecurityGui {
    /** Width of one access LED, including its 1px gap. */
    static final int LED_PITCH = 7;

    private SecurityGui() {
    }

    static int color(ScreenStyle style, PaletteColor color) {
        return style.getColor(color).toARGB();
    }

    /** A widget box from the style, relative to the top-left corner of a frame of the given size. */
    static Rect2i box(ScreenStyle style, String id, int frameWidth, int frameHeight) {
        WidgetStyle widget = style.getWidget(id);
        var origin = widget.resolve(new Rect2i(0, 0, frameWidth, frameHeight));
        return new Rect2i(origin.getX(), origin.getY(), widget.getWidth(), widget.getHeight());
    }

    /**
     * Stretches an AE2 texture region to any size, keeping its edges: the corners are copied,
     * the edges and center stretched. Edges must be uniform along their length.
     */
    static void nineSlice(GuiGraphics graphics, Blitter source, int left, int top, int right, int bottom,
            int x, int y, int width, int height) {
        int sx = source.getSrcX();
        int sy = source.getSrcY();
        int sw = source.getSrcWidth();
        int sh = source.getSrcHeight();
        int[] srcX = {sx, sx + left, sx + sw - right};
        int[] srcW = {left, sw - left - right, right};
        int[] dstX = {x, x + left, x + width - right};
        int[] dstW = {left, width - left - right, right};
        int[] srcY = {sy, sy + top, sy + sh - bottom};
        int[] srcH = {top, sh - top - bottom, bottom};
        int[] dstY = {y, y + top, y + height - bottom};
        int[] dstH = {top, height - top - bottom, bottom};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (dstW[col] <= 0 || dstH[row] <= 0 || srcW[col] <= 0 || srcH[row] <= 0) continue;
                source.copy()
                        .src(srcX[col], srcY[row], srcW[col], srcH[row])
                        .dest(dstX[col], dstY[row], dstW[col], dstH[row])
                        .blit(graphics);
            }
        }
    }

    /** AE2's inset well (the CPU list track): a white frame with a shaded top edge. */
    static void well(GuiGraphics graphics, ScreenStyle style, int x, int y, int width, int height) {
        nineSlice(graphics, style.getImage("well"), 1, 3, 1, 1, x, y, width, height);
    }

    /** An 18px AE2 slot with an icon in it, faded when the icon stands for something not granted. */
    static void iconSlot(GuiGraphics graphics, Icon icon, int x, int y, float iconOpacity) {
        Icon.SLOT_BACKGROUND.getBlitter().dest(x, y).blit(graphics);
        icon.getBlitter().opacity(iconOpacity).dest(x + 1, y + 1).blit(graphics);
    }

    /** An AE2 icon recolored to a palette color, so a white toolbar glyph can sit inline with text. */
    static void tintedIcon(GuiGraphics graphics, Icon icon, int x, int y, int argb) {
        icon.getBlitter().colorRgb(argb & 0xFFFFFF).dest(x, y).blit(graphics);
    }

    /**
     * One LED per editable permission, in {@link Permission#editable()} order: lit in AE2's
     * selection blue when granted, dark when not. Dark-for-off reads the same on a plain and
     * on a selected (blue) row.
     */
    static void accessLeds(GuiGraphics graphics, ScreenStyle style, int x, int y, int mask) {
        int frame = color(style, PaletteColor.DEFAULT_TEXT_COLOR);
        int lit = color(style, PaletteColor.SELECTION_COLOR);
        var permissions = Permission.editable();
        for (int i = 0; i < permissions.size(); i++) {
            int left = x + i * LED_PITCH;
            graphics.fill(left, y, left + LED_PITCH - 1, y + 5, frame);
            if (permissions.get(i).in(mask)) graphics.fill(left + 1, y + 1, left + LED_PITCH - 2, y + 4, lit);
        }
    }

    /**
     * A warning for a network that is not normally protected, right-aligned to {@code right}:
     * an open lock once disconnected (a verified detached network is public), and AE2's red
     * "invalid" sign for a conflict. A protected network, the normal state, shows nothing.
     */
    static void networkWarning(GuiGraphics graphics, ScreenStyle style, Font font, String status, int right, int y) {
        if (status.equals("protected")) return;
        var label = Component.translatable("screen.ae2security." + status);
        int argb = color(style, PaletteColor.ERROR);
        int textX = right - font.width(label);
        text(graphics, font, label, textX, y, argb);
        if (status.equals("disconnected")) {
            tintedIcon(graphics, Icon.UNLOCKED, textX - 15, y - 4, argb);
        } else {
            Icon.INVALID.getBlitter().dest(textX - 17, y - 4).blit(graphics);
        }
    }

    /** Text without a drop shadow, like every AE2 label. */
    static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int argb) {
        graphics.drawString(font, text, x, y, argb, false);
    }

    static void text(GuiGraphics graphics, Font font, String text, int x, int y, int argb) {
        graphics.drawString(font, text, x, y, argb, false);
    }

    static void centeredText(GuiGraphics graphics, Font font, Component text, int centerX, int y, int argb) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, argb, false);
    }

    /** Word-wrapped, centered lines; returns the y below the last line. */
    static int wrappedCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int width,
            int argb) {
        for (var line : font.split(text, width)) {
            graphics.drawString(font, line, centerX - font.width(line) / 2, y, argb, false);
            y += font.lineHeight;
        }
        return y;
    }
}
