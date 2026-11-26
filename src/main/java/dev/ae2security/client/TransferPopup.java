package dev.ae2security.client;

import java.util.List;
import java.util.Locale;

import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import dev.ae2security.network.PolicyEditResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The ownership transfer confirmation, drawn over the dimmed Security screen in AE2's generated
 * frame. AE2's own sub-screens ({@code AESubScreen}) swap the whole menu on the server, which
 * the Security menu does not support, so this is an overlay owned by the screen: the screen
 * routes all input here while it is open.
 * <p>
 * The warning lives in the toolbar tab's tooltip; the popup only asks for the name. A pending
 * or failed transfer replaces the prompt line, so the layout never moves.
 */
final class TransferPopup {
    /** Fits "Type WWWWWWWWWWWWWWWW to confirm", the widest prompt, on one line. */
    static final int WIDTH = 196;
    private static final int MARGIN = 8;
    private static final int TITLE_Y = 7;
    private static final int PROMPT_Y = 21;
    private static final int FIELD_Y = 32;
    private static final int BUTTONS_Y = 50;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_HEIGHT = 18;
    private static final int HEIGHT = BUTTONS_Y + BUTTON_HEIGHT + 7;
    private static final int DIM = 0xA0101018;

    private final ScreenStyle style;
    private final Font font;
    private final int x;
    private final int y;
    final AETextField field;
    final AE2Button cancel;
    final AE2Button confirm;

    TransferPopup(ScreenStyle style, Font font, int screenWidth, int screenHeight, Runnable onConfirm,
            Runnable onCancel) {
        this.style = style;
        this.font = font;
        x = (screenWidth - WIDTH) / 2;
        y = (screenHeight - HEIGHT) / 2;
        int inner = WIDTH - 2 * MARGIN;
        field = new FullWidthSearchField(style, font, x + MARGIN, y + FIELD_Y, inner, 12);
        field.setBordered(false);
        field.setMaxLength(64);
        field.setPlaceholder(Component.translatable("screen.ae2security.transfer_name"));
        field.setMessage(Component.translatable("screen.ae2security.transfer_name"));
        int buttonWidth = (inner - BUTTON_GAP) / 2;
        cancel = new AE2Button(x + MARGIN, y + BUTTONS_Y, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.cancel"), button -> onCancel.run());
        confirm = new AE2Button(x + WIDTH - MARGIN - buttonWidth, y + BUTTONS_Y, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("screen.ae2security.confirm_transfer"), button -> onConfirm.run());
    }

    /** What a key press asks the screen to do. */
    enum KeyAction { PASS, CONFIRM, CANCEL }

    List<AbstractWidget> widgets() {
        return List.of(field, cancel, confirm);
    }

    /** Escape cancels, Enter confirms once the name matches; every other key goes to the name field. */
    KeyAction keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return KeyAction.CANCEL;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return confirm.active ? KeyAction.CONFIRM : KeyAction.PASS;
        }
        field.keyPressed(keyCode, scanCode, modifiers);
        return KeyAction.PASS;
    }

    void mouseClicked(double mouseX, double mouseY, int button) {
        for (var widget : widgets()) {
            if (widget.mouseClicked(mouseX, mouseY, button)) return;
        }
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (var widget : widgets()) {
            widget.mouseReleased(mouseX, mouseY, button);
        }
        return true;
    }

    /**
     * Dims everything drawn so far and draws the popup above it.
     *
     * @param name the player who would receive ownership
     * @param status the transfer's edit result to show in place of the prompt, or null
     */
    void render(GuiGraphics graphics, int screenWidth, int screenHeight, String name, boolean pending,
            PolicyEditResult.Result status, int mouseX, int mouseY, float partialTick) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        graphics.fill(0, 0, screenWidth, screenHeight, DIM);
        BackgroundGenerator.draw(WIDTH, HEIGHT, graphics, x, y);

        int text = SecurityGui.color(style, PaletteColor.DEFAULT_TEXT_COLOR);
        int inner = WIDTH - 2 * MARGIN;
        int left = x + MARGIN;
        SecurityGui.text(graphics, font, Component.translatable("screen.ae2security.transfer"), left, y + TITLE_Y,
                text);
        if (pending || status != null) {
            var message = Component.translatable(pending ? "screen.ae2security.pending"
                    : "screen.ae2security.edit_result." + status.name().toLowerCase(Locale.ROOT));
            int color = pending ? SecurityGui.color(style, PaletteColor.MUTED_TEXT_COLOR)
                    : status == PolicyEditResult.Result.APPLIED ? text : SecurityGui.color(style, PaletteColor.ERROR);
            drawLine(graphics, message, left, y + PROMPT_Y, inner, color);
        } else {
            var prompt = SecurityScreenText.firstThatFits(List.of(
                    Component.translatable("screen.ae2security.transfer_prompt", name).getString(),
                    Component.translatable("screen.ae2security.transfer_prompt_short", name).getString(),
                    name), inner, font::width);
            drawLine(graphics, Component.literal(prompt), left, y + PROMPT_Y, inner, text);
        }
        for (var widget : widgets()) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        pose.popPose();
    }

    /** One line that never clips: text wider than the popup (a long translation) is drawn smaller. */
    private void drawLine(GuiGraphics graphics, Component line, int left, int top, int width, int color) {
        int textWidth = font.width(line);
        if (textWidth <= width) {
            graphics.drawString(font, line, left, top, color, false);
            return;
        }
        float scale = (float) width / textWidth;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top + font.lineHeight * (1 - scale) / 2, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, line, 0, 0, color, false);
        pose.popPose();
    }
}
