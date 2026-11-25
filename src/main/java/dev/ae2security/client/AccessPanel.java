package dev.ae2security.client;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
 * Layout and text of the right-hand column, the selected player's access. Its widgets belong
 * to the screen; this class places them and draws everything else: the empty and untrusted
 * states, the transfer prompt and the edit status. The selected player is named by their
 * highlighted list row, and by the list footer while they are on another page.
 * <p>
 * The permission switches start level with the search field and Trust/Remove trust follows
 * them; the transfer group is anchored to the bottom, level with the bottom of the list.
 */
final class AccessPanel {
    static final int BUTTON_HEIGHT = 18;
    private static final int TRUST_GAP = 4;
    // The transfer group is anchored to the column's bottom edge.
    private static final int TRANSFER_FROM_BOTTOM = 18;
    private static final int TRANSFER_NAME_FROM_BOTTOM = 32;
    private static final int STATUS_LINE_FROM_BOTTOM = 42;
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
        return box.getY() + row * PermissionSwitch.HEIGHT;
    }

    int trustY() {
        return permissionY(Permission.editable().size()) + TRUST_GAP;
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
}
