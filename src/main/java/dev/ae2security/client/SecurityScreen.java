package dev.ae2security.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.OpenGuideButton;
import dev.ae2security.MESecurity;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import dev.ae2security.menu.SecurityMenu;
import dev.ae2security.network.EditPolicy;
import dev.ae2security.network.PolicySnapshot;
import dev.ae2security.security.OwnershipConfirmation;
import dev.ae2security.security.Permission;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SecurityScreen extends AbstractContainerScreen<SecurityMenu> {
    private static final int PAGE_SIZE = 6;
    private static final int PERMISSION_TOP = 58;
    private static final int PERMISSION_ROW_HEIGHT = 22;

    private static final int AE2_DARK = 0xFF413F54;
    private static final int AE2_BACKGROUND = 0xFFCBCCD4;
    private static final int AE2_PANEL = 0xFFB6B9C8;
    private static final int AE2_HIGHLIGHT = 0xFFF2F2F2;
    private static final int AE2_SHADOW = 0xFF878FA5;
    private static final int AE2_MUTED_TEXT = 0xFF687087;
    private static final int AE2_ACCENT = 0xFF9C8FD0;
    private static final int STATUS_WARNING = 0xFF9B3E24;

    private final ScreenStyle style;
    private final PlayerButton[] playerButtons = new PlayerButton[PAGE_SIZE];
    private final Map<Permission, PermissionTabButton> permissionButtons = new EnumMap<>(Permission.class);

    private AETextField search;
    private AETextField transferName;
    private AE2Button previousPageButton;
    private AE2Button nextPageButton;
    private AE2Button trustButton;
    private AE2Button transferButton;
    private List<PolicySnapshot.PlayerEntry> visiblePlayers = List.of();

    private String query = "";
    private int page;
    private UUID selected;
    private UUID transferConfirmation;
    private String transferInput = "";
    private PolicySnapshot displayed;
    private boolean pending;

    public SecurityScreen(SecurityMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.style = StyleManager.loadStyleDoc("/screens/terminals/base_terminal.json");
        imageWidth = 304;
        imageHeight = 218;
    }

    private List<PolicySnapshot.PlayerEntry> filtered() {
        var snapshot = menu.snapshot();
        if (snapshot == null) {
            return List.of();
        }

        String filter = query.toLowerCase(Locale.ROOT);
        return snapshot.players().stream()
                .filter(player -> player.name().toLowerCase(Locale.ROOT).contains(filter)
                        || player.id().toString().startsWith(filter))
                .toList();
    }

    private PolicySnapshot.PlayerEntry selection() {
        var snapshot = menu.snapshot();
        if (snapshot == null) {
            return null;
        }
        return snapshot.players().stream()
                .filter(player -> player.id().equals(selected))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void init() {
        super.init();

        var help = addRenderableWidget(new OpenGuideButton(button -> {
            if (minecraft != null && minecraft.player != null) {
                GuidesCommon.openGuide(minecraft.player, net.minecraft.resources.ResourceLocation.parse("ae2:guide"),
                        PageAnchor.page(MESecurity.id("security_terminal.md")));
            }
        }));
        help.setX(leftPos + imageWidth - 22);
        help.setY(topPos + 5);
        help.setTooltip(Tooltip.create(Component.translatable("screen.ae2security.guide")));

        // Match the 89x12 search field used by AE2's storage terminals. AETextField
        // renders a fixed-width background texture and does not stretch cleanly.
        search = addRenderableWidget(new AETextField(style, font, leftPos + 63, topPos + 54, 89, 12));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setPlaceholder(Component.translatable("screen.ae2security.search"));
        search.setTooltipMessage(List.of(Component.translatable("screen.ae2security.search_tooltip")));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            clearTransferConfirmation();
            refreshControls();
        });

        for (int row = 0; row < PAGE_SIZE; row++) {
            int rowIndex = row;
            playerButtons[row] = addRenderableWidget(new PlayerButton(
                    leftPos + 12, topPos + 74 + row * 18, 140, 17, Component.empty(),
                    button -> selectVisiblePlayer(rowIndex)));
        }

        previousPageButton = ae2Button(12, 186, 34, 18, Component.literal("<"), () -> {
            page--;
            clearTransferConfirmation();
            refreshControls();
        });
        nextPageButton = ae2Button(118, 186, 34, 18, Component.literal(">"), () -> {
            page++;
            clearTransferConfirmation();
            refreshControls();
        });

        for (int row = 0; row < Permission.editable().size(); row++) {
            var permission = Permission.editable().get(row);
            var button = new PermissionTabButton(permissionIcon(permission),
                    Component.translatable("permission.ae2security."
                            + permission.name().toLowerCase(Locale.ROOT)),
                    pressed -> togglePermission(permission));
            button.setX(leftPos + 270);
            button.setY(topPos + PERMISSION_TOP + row * PERMISSION_ROW_HEIGHT);
            permissionButtons.put(permission, addRenderableWidget(button));
        }

        trustButton = ae2Button(164, 147, 128, 18, Component.empty(), this::toggleTrust);
        transferName = addRenderableWidget(new AETextField(style, font, leftPos + 164, topPos + 176, 128, 12));
        transferName.setBordered(false);
        transferName.setMaxLength(64);
        transferName.setPlaceholder(Component.translatable("screen.ae2security.transfer_name"));
        transferName.setValue(transferInput);
        transferName.setResponder(value -> {
            transferInput = value;
            refreshControls();
        });
        transferButton = ae2Button(164, 190, 128, 18, Component.empty(), this::transferOwnership);
        transferButton.setTooltip(Tooltip.create(Component.translatable("screen.ae2security.transfer_warning")));

        displayed = menu.snapshot();
        refreshControls();
        setInitialFocus(search);
    }

    private AE2Button ae2Button(int x, int y, int width, int height, Component label, Runnable action) {
        return addRenderableWidget(new AE2Button(leftPos + x, topPos + y, width, height, label,
                button -> action.run()));
    }

    private static Icon permissionIcon(Permission permission) {
        return switch (permission) {
            case VIEW -> Icon.VIEW_MODE_STORED;
            case INSERT -> Icon.ACCESS_WRITE;
            case EXTRACT -> Icon.ACCESS_READ;
            case CRAFT -> Icon.CRAFT_HAMMER;
            case BUILD -> Icon.PLACEMENT_ITEM;
        };
    }

    private void selectVisiblePlayer(int row) {
        if (row >= visiblePlayers.size()) {
            return;
        }
        selected = visiblePlayers.get(row).id();
        clearTransferConfirmation();
        refreshControls();
    }

    private void togglePermission(Permission permission) {
        var player = selection();
        if (player == null) {
            return;
        }

        send(EditPolicy.Action.TOGGLE_PERMISSION, permission.bit());
    }

    private void toggleTrust() {
        var player = selection();
        if (player != null) {
            send(player.trusted() ? EditPolicy.Action.REMOVE : EditPolicy.Action.TRUST, Permission.VIEW.bit());
        }
    }

    private void transferOwnership() {
        var player = selection();
        if (player == null) {
            return;
        }

        if (player.id().equals(transferConfirmation)) {
            if (OwnershipConfirmation.matches(player.name(), transferInput)) {
                send(EditPolicy.Action.TRANSFER, 0, transferInput);
            } else if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "message.ae2security.transfer_name_mismatch", player.name()), true);
                setFocused(transferName);
            }
        } else {
            transferConfirmation = player.id();
            transferInput = "";
            transferName.setValue("");
            refreshControls();
            setFocused(transferName);
        }
    }

    private void refreshControls() {
        if (playerButtons[0] == null) {
            return;
        }

        var rows = filtered();
        int lastPage = Math.max(0, (rows.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, lastPage));
        int first = page * PAGE_SIZE;
        visiblePlayers = rows.subList(first, Math.min(rows.size(), first + PAGE_SIZE));

        for (int row = 0; row < PAGE_SIZE; row++) {
            var button = playerButtons[row];
            if (row >= visiblePlayers.size()) {
                button.visible = false;
                button.active = false;
                continue;
            }

            var player = visiblePlayers.get(row);
            String marker = player.trusted() ? "+ " : "  ";
            button.setMessage(Component.literal(marker + font.plainSubstrByWidth(player.name(), 116)));
            button.setTooltip(Tooltip.create(SecurityScreenText.playerTooltip(player)));
            button.setSelected(player.id().equals(selected));
            button.visible = true;
            button.active = true;
        }

        previousPageButton.active = page > 0;
        nextPageButton.active = page < lastPage;

        var player = selection();
        boolean trusted = player != null && player.trusted();
        for (var permission : Permission.editable()) {
            boolean enabled = player != null && permission.in(player.permissions());
            String permissionKey = "permission.ae2security." + permission.name().toLowerCase(Locale.ROOT);
            var button = permissionButtons.get(permission);
            var name = Component.translatable(permissionKey);
            var description = Component.translatable(permissionKey + ".description");
            button.setSelected(enabled);
            button.visible = trusted;
            button.active = trusted;
            button.setTooltip(Tooltip.create(Component.empty()
                    .append(Component.translatable(
                            enabled ? "screen.ae2security.permission_enabled"
                                    : "screen.ae2security.permission_disabled",
                            name))
                    .append("\n")
                    .append(description)));
        }

        trustButton.setMessage(Component.translatable(
                trusted ? "screen.ae2security.remove" : "screen.ae2security.trust"));
        trustButton.setY(topPos + (trusted ? 147 : 116));
        trustButton.visible = player != null;
        trustButton.active = player != null;

        boolean confirming = trusted && player.id().equals(transferConfirmation);
        transferName.visible = confirming;
        transferName.active = confirming;
        transferButton.setMessage(Component.translatable(
                confirming ? "screen.ae2security.confirm_transfer" : "screen.ae2security.transfer"));
        transferButton.visible = trusted;
        transferButton.active = trusted
                && (!confirming || OwnershipConfirmation.matches(player.name(), transferInput));
    }

    private void send(EditPolicy.Action action, int mask) {
        send(action, mask, "");
    }

    private void send(EditPolicy.Action action, int mask, String confirmation) {
        var snapshot = menu.snapshot();
        boolean toggle = action == EditPolicy.Action.TOGGLE_PERMISSION;
        if (snapshot == null || selected == null || pending && !toggle) {
            return;
        }

        if (!toggle) pending = true;
        PacketDistributor.sendToServer(new EditPolicy(menu.containerId, snapshot.revision(), selected, action, mask,
                confirmation));
        refreshControls();
    }

    private void clearTransferConfirmation() {
        transferConfirmation = null;
        transferInput = "";
        if (transferName != null) transferName.setValue("");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (displayed != menu.snapshot()) {
            displayed = menu.snapshot();
            pending = false;
            clearTransferConfirmation();
            refreshControls();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((search != null && search.isFocused() || transferName != null && transferName.isFocused())
                && minecraft != null
                && minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            // Inventory keys are configurable. When the focused field receives that character,
            // keep the screen open and let the matching charTyped event enter it normally.
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        drawRaisedPanel(graphics, x, y, imageWidth, imageHeight);
        drawInsetPanel(graphics, x + 7, y + 38, 151, 174);
        drawInsetPanel(graphics, x + 159, y + 38, 138, 174);
        drawSecurityGlyph(graphics, x + 9, y + 7);
    }

    private static void drawRaisedPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, AE2_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, AE2_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 3, AE2_BACKGROUND);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, AE2_SHADOW);
    }

    private static void drawInsetPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, AE2_SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, AE2_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + width - 2, y + height - 2, AE2_PANEL);
    }

    private static void drawSecurityGlyph(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 2, y, x + 7, y + 1, AE2_DARK);
        graphics.fill(x + 1, y + 1, x + 3, y + 5, AE2_DARK);
        graphics.fill(x + 6, y + 1, x + 8, y + 5, AE2_DARK);
        graphics.fill(x + 3, y + 1, x + 6, y + 2, AE2_ACCENT);
        graphics.fill(x, y + 4, x + 9, y + 11, AE2_DARK);
        graphics.fill(x + 1, y + 5, x + 8, y + 10, AE2_ACCENT);
        graphics.fill(x + 4, y + 6, x + 5, y + 9, AE2_DARK);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 22, 9, AE2_DARK, false);

        var snapshot = menu.snapshot();
        if (snapshot == null) {
            graphics.drawString(font, Component.translatable("screen.ae2security.loading"), 9, 24,
                    AE2_MUTED_TEXT, false);
            return;
        }

        graphics.drawString(font,
                font.plainSubstrByWidth(Component.translatable("screen.ae2security.owner", snapshot.ownerName()).getString(), 190),
                9, 24, AE2_MUTED_TEXT, false);

        if (!snapshot.status().equals("protected")) {
            var status = Component.translatable("screen.ae2security." + snapshot.status());
            graphics.drawString(font, status, imageWidth - 9 - font.width(status), 24, STATUS_WARNING, false);
        }

        graphics.drawString(font, Component.translatable("screen.ae2security.players"), 12, 43, AE2_DARK, false);

        var selectedPlayer = selection();
        String accessHeading = selectedPlayer == null
                ? Component.translatable("screen.ae2security.select").getString()
                : Component.translatable("screen.ae2security.access_for", selectedPlayer.name()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(accessHeading, 128), 164, 43,
                selectedPlayer == null ? AE2_MUTED_TEXT : AE2_DARK, false);

        if (selectedPlayer != null && selectedPlayer.trusted()) {
            for (int row = 0; row < Permission.editable().size(); row++) {
                var permission = Permission.editable().get(row);
                var label = Component.translatable(
                        "permission.ae2security." + permission.name().toLowerCase(Locale.ROOT));
                graphics.drawString(font, label, 165,
                        PERMISSION_TOP + 4 + row * PERMISSION_ROW_HEIGHT,
                        AE2_DARK, false);
            }
        }

        if (selectedPlayer != null && selectedPlayer.id().equals(transferConfirmation)) {
            var prompt = Component.translatable("screen.ae2security.transfer_prompt", selectedPlayer.name());
            graphics.drawString(font, font.plainSubstrByWidth(prompt.getString(), 128), 164, 166,
                    AE2_MUTED_TEXT, false);
        }

        var rows = filtered();
        String pages = (page + 1) + " / " + Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawCenteredString(font, pages, 82, 191, AE2_DARK);
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.ae2security.no_players"), 82, 98,
                    AE2_MUTED_TEXT);
        }

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** A TabButton with a compact raised/off and pressed/on background. */
    private static final class PermissionTabButton extends TabButton {
        PermissionTabButton(Icon icon, Component message, OnPress onPress) {
            super(icon, message, onPress);
            setStyle(Style.BOX);
            setDisableBackground(true);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!visible) return;
            int x = getX();
            int y = getY();
            if (isSelected()) {
                graphics.fill(x, y, x + 20, y + 20, AE2_DARK);
                graphics.fill(x + 1, y + 1, x + 19, y + 19, AE2_SHADOW);
                graphics.fill(x + 2, y + 2, x + 19, y + 19, AE2_ACCENT);
                graphics.fill(x + 2, y + 18, x + 19, y + 19, AE2_HIGHLIGHT);
                graphics.fill(x + 18, y + 2, x + 19, y + 19, AE2_HIGHLIGHT);
            } else {
                drawRaisedPanel(graphics, x, y, 20, 20);
            }
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** AE2 player-row button with a persistent accent outline for the active selection. */
    private static final class PlayerButton extends AE2Button {
        private boolean selected;

        PlayerButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress);
        }

        void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (!selected) return;

            int x = getX();
            int y = getY();
            int right = x + getWidth();
            int bottom = y + getHeight();
            graphics.fill(x - 1, y - 1, right + 1, y, AE2_ACCENT);
            graphics.fill(x - 1, bottom, right + 1, bottom + 1, AE2_ACCENT);
            graphics.fill(x - 1, y, x, bottom, AE2_ACCENT);
            graphics.fill(right, y, right + 1, bottom, AE2_ACCENT);
        }
    }
}
