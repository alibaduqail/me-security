package dev.ae2security.client;

import java.util.Locale;

import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import dev.ae2security.network.PolicyEditResult;
import dev.ae2security.network.PolicySnapshot;
import dev.ae2security.security.Permission;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/**
 * Layout and text of the right-hand column, the selected player's access: the five permission
 * switches, Trust/Remove trust and the edit status under it. Ownership transfer lives in its own
 * toolbar tab and popup. The selected player is named by their highlighted list row, and by
 * the list footer while they are on another page.
 * <p>
 * The switches start level with the search field; the status line's second row ends level with
 * the bottom of the list, so the column has no space it does not use.
 */
final class AccessPanel {
    static final int BUTTON_HEIGHT = 18;
    /** An 18px slot row plus a 2px gap. */
    private static final int ROW_PITCH = 20;
    private static final int TRUST_GAP = 2;
    private static final int STATUS_GAP = 3;
    /** Height of the "Select a player" block: slot, heading and a hint of up to three lines. */
    private static final int EMPTY_STATE_HEIGHT = 64;

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
        return box.getY() + row * ROW_PITCH;
    }

    int trustY() {
        return permissionY(Permission.editable().size()) + TRUST_GAP;
    }

    private int statusY() {
        return trustY() + BUTTON_HEIGHT + STATUS_GAP;
    }

    /**
     * Draws the column's text, relative to the frame.
     *
     * @param editStatus the result of the last blocking edit, or null
     * @param statusOwnedByPopup whether the transfer popup is open and shows the status itself
     */
    void draw(GuiGraphics graphics, PolicySnapshot.PlayerEntry selected, boolean pending,
            PolicyEditResult.Result editStatus, boolean statusOwnedByPopup) {
        int text = SecurityGui.color(style, PaletteColor.DEFAULT_TEXT_COLOR);
        int muted = SecurityGui.color(style, PaletteColor.MUTED_TEXT_COLOR);
        int x = box.getX();
        int y = box.getY();
        int width = box.getWidth();
        int centerX = x + width / 2;

        if (selected == null) {
            int top = y + (box.getHeight() - EMPTY_STATE_HEIGHT) / 2;
            SecurityGui.iconSlot(graphics, Icon.LOCKED, centerX - 9, top, 1f);
            SecurityGui.centeredText(graphics, font, Component.translatable("screen.ae2security.select"),
                    centerX, top + 24, text);
            SecurityGui.wrappedCentered(graphics, font, Component.translatable("screen.ae2security.select_hint"),
                    centerX, top + 37, width - 4, muted);
        } else if (!selected.trusted()) {
            // Centered in the space the permission switches take for a trusted player.
            var hint = Component.translatable("screen.ae2security.untrusted_hint");
            int top = permissionY(0);
            int space = trustY() - top;
            int lines = font.split(hint, width - 4).size();
            SecurityGui.wrappedCentered(graphics, font, hint, centerX,
                    top + (space - lines * font.lineHeight) / 2, width - 4, muted);
        }

        if (!statusOwnedByPopup && (pending || editStatus != null)) {
            var status = Component.translatable(pending ? "screen.ae2security.pending"
                    : "screen.ae2security.edit_result." + editStatus.name().toLowerCase(Locale.ROOT));
            int color = pending ? muted : editStatus == PolicyEditResult.Result.APPLIED ? text
                    : SecurityGui.color(style, PaletteColor.ERROR);
            var lines = font.split(status, width);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), x, statusY() + i * font.lineHeight, color, false);
            }
        }
    }
}
