package dev.ae2security.client;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import dev.ae2security.network.PolicyEditResult;
import dev.ae2security.network.PolicySnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/**
 * Layout and text of the right-hand column, the selected player's access. Its widgets belong
 * to the screen; this class places them and draws everything else: the player header, the
 * empty and untrusted states, the transfer prompt and the edit status.
 */
final class AccessPanel {
    static final int BUTTON_HEIGHT = 18;
    private static final int HEADER_TEXT_LEFT = 22;
    private static final int PERMISSIONS_TOP = 22;
    // The actions are anchored to the column's bottom edge.
    private static final int TRANSFER_FROM_BOTTOM = 18;
    private static final int TRANSFER_NAME_FROM_BOTTOM = 32;
    private static final int STATUS_LINE_FROM_BOTTOM = 42;
    private static final int TRUST_FROM_BOTTOM = 63;

    private final ScreenStyle style;
    private final Font font;
    private final Rect2i box;

    AccessPanel(ScreenStyle style, Font font, Rect2i box) {
        this.style = style;
        this.font = font;
        this.box = box;
    }

    int left() {
        return box.getX();
    }

    int width() {
        return box.getWidth();
    }

    int permissionY(int row) {
        return box.getY() + PERMISSIONS_TOP + row * PermissionSwitch.HEIGHT;
    }

    int trustY() {
        return bottom() - TRUST_FROM_BOTTOM;
    }

    int transferNameY() {
        return bottom() - TRANSFER_NAME_FROM_BOTTOM;
    }

    int transferY() {
        return bottom() - TRANSFER_FROM_BOTTOM;
    }

    private int bottom() {
        return box.getY() + box.getHeight();
    }

    /**
     * Draws the column's text, relative to the frame.
     *
     * @param transferConfirmation the player whose ownership transfer awaits their typed name, or null
     * @param editStatus the result of the last blocking edit, or null
     */
    void draw(GuiGraphics graphics, PolicySnapshot.PlayerEntry selected, UUID transferConfirmation,
            boolean pending, PolicyEditResult.Result editStatus) {
        int text = SecurityGui.color(style, PaletteColor.DEFAULT_TEXT_COLOR);
        int muted = SecurityGui.color(style, PaletteColor.MUTED_TEXT_COLOR);
        int x = box.getX();
        int y = box.getY();
        int width = box.getWidth();
        int centerX = x + width / 2;

        if (selected == null) {
            int top = y + 46;
            SecurityGui.iconSlot(graphics, Icon.LOCKED, centerX - 9, top, 1f);
            SecurityGui.centeredText(graphics, font, Component.translatable("screen.ae2security.select"),
                    centerX, top + 24, text);
            SecurityGui.wrappedCentered(graphics, font, Component.translatable("screen.ae2security.select_hint"),
                    centerX, top + 37, width - 4, muted);
        } else {
            drawHeader(graphics, selected, text, muted);
            if (!selected.trusted()) {
                // Centered in the space the permission switches take for a trusted player.
                var hint = Component.translatable("screen.ae2security.untrusted_hint");
                int top = permissionY(0);
                int space = trustY() - top;
                int lines = font.split(hint, width - 4).size();
                SecurityGui.wrappedCentered(graphics, font, hint, centerX,
                        top + (space - lines * font.lineHeight) / 2, width - 4, muted);
            }
        }

        // The transfer prompt and the edit status share one line; the status wins while an edit is pending.
        int lineY = bottom() - STATUS_LINE_FROM_BOTTOM;
        if (selected != null && selected.id().equals(transferConfirmation) && !pending) {
            var name = selected.name();
            var prompt = SecurityScreenText.firstThatFits(List.of(
                    Component.translatable("screen.ae2security.transfer_prompt", name).getString(),
                    Component.translatable("screen.ae2security.transfer_prompt_short", name).getString(),
                    name), width, font::width);
            SecurityGui.text(graphics, font, font.plainSubstrByWidth(prompt, width), x, lineY, muted);
        }
        if (pending || editStatus != null && transferConfirmation == null) {
            var status = Component.translatable(pending ? "screen.ae2security.pending"
                    : "screen.ae2security.edit_result." + editStatus.name().toLowerCase(Locale.ROOT));
            int color = pending ? muted : editStatus == PolicyEditResult.Result.APPLIED ? text
                    : SecurityGui.color(style, PaletteColor.ERROR);
            // A result is only shown while no transfer is being confirmed, so the name field's
            // row is free for a second line.
            var lines = font.split(status, width);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), x, lineY + i * font.lineHeight, color, false);
            }
        }
    }

    /** A slot with the player's lock, the "Name's Access" heading (or just the name) and their summary. */
    private void drawHeader(GuiGraphics graphics, PolicySnapshot.PlayerEntry selected, int text, int muted) {
        int x = box.getX();
        int y = box.getY();
        int headingWidth = box.getWidth() - HEADER_TEXT_LEFT;
        SecurityGui.iconSlot(graphics, selected.trusted() ? Icon.UNLOCKED : Icon.LOCKED, x, y, 1f);
        String heading = SecurityScreenText.firstThatFits(List.of(
                Component.translatable("screen.ae2security.access_for", selected.name()).getString(),
                selected.name()), headingWidth, font::width);
        SecurityGui.text(graphics, font, font.plainSubstrByWidth(heading, headingWidth),
                x + HEADER_TEXT_LEFT, y, text);
        SecurityGui.text(graphics, font, SecurityScreenText.accessSummary(selected),
                x + HEADER_TEXT_LEFT, y + 10, muted);
    }
}
