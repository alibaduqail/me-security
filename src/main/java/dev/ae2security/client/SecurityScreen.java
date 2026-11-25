package dev.ae2security.client;

import java.util.EnumMap;
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
import dev.ae2security.network.PolicyEditResult;
import dev.ae2security.network.PlayerSearchResult;
import dev.ae2security.security.OwnershipConfirmation;
import dev.ae2security.security.Permission;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SecurityScreen extends AbstractContainerScreen<SecurityMenu> {
    private static final int PAGE_SIZE = PlayerSearchResult.PAGE_SIZE;
    private static final int PLAYER_LIST_WIDTH = 140;
    private static final int SEARCH_FIELD_LEFT_INSET = 3;
    private static final int SEARCH_FIELD_RIGHT_INSET = 5;
    private static final int AE_TEXT_FIELD_TEXTURE_SIZE = 128;
    // Vertical layout of the right-hand panel (y relative to the screen, inset panel is 38..226):
    //   heading text 43..51; permission buttons 54 + 21 * row, 20 high: 54..74, 75..95, 96..116, 117..137, 138..158
    //   trust button 160..178; transfer prompt / edit status text 180..188; name field 190..202;
    //   transfer button 204..222. The player list pager (left panel) shares the 204..222 band.
    private static final int PERMISSION_TOP = 54;
    private static final int PERMISSION_ROW_HEIGHT = 21;
    private static final int TRUST_BUTTON_Y = 160;
    private static final int TRUST_BUTTON_Y_UNTRUSTED = 116;
    private static final int TRANSFER_TEXT_Y = 180;
    private static final int TRANSFER_NAME_Y = 190;
    private static final int TRANSFER_BUTTON_Y = 204;
    private static final int PLAYER_ROW_TOP = 74;
    private static final int PLAYER_ROW_PITCH = 21;
    private static final int PAGER_Y = 204;
    private static final ResourceLocation AE_TEXT_FIELD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/text_field.png");

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

    private final PlayerListState players = new PlayerListState();
    private final PendingEdits edits = new PendingEdits();
    private UUID transferConfirmation;
    private String transferInput = "";
    private PolicySnapshot displayed;

    public SecurityScreen(SecurityMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.style = StyleManager.loadStyleDoc("/screens/terminals/base_terminal.json");
        imageWidth = 304;
        imageHeight = 232;
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

        search = addRenderableWidget(new FullWidthSearchField(
                style, font, leftPos + 12 + SEARCH_FIELD_LEFT_INSET + 2, topPos + 54,
                PLAYER_LIST_WIDTH - SEARCH_FIELD_LEFT_INSET - SEARCH_FIELD_RIGHT_INSET, 12));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setPlaceholder(Component.translatable("screen.ae2security.search"));
        search.setMessage(Component.translatable("screen.ae2security.search"));
        search.setTooltip(Tooltip.create(Component.translatable("screen.ae2security.search_tooltip")));
        search.setValue(players.query());
        search.setResponder(value -> {
            players.setQuery(value);
            clearTransferConfirmation();
            refreshControls();
        });

        for (int row = 0; row < PAGE_SIZE; row++) {
            int rowIndex = row;
            playerButtons[row] = addRenderableWidget(new PlayerButton(
                    leftPos + 12, topPos + PLAYER_ROW_TOP + row * PLAYER_ROW_PITCH, PLAYER_LIST_WIDTH, 17, Component.empty(),
                    button -> selectVisiblePlayer(rowIndex)));
        }

        previousPageButton = ae2Button(12, PAGER_Y, 34, 18, Component.literal("<"), () -> turnPage(-1));
        nextPageButton = ae2Button(118, PAGER_Y, 34, 18, Component.literal(">"), () -> turnPage(1));

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

        trustButton = ae2Button(164, TRUST_BUTTON_Y, 128, 18, Component.empty(), this::toggleTrust);
        transferName = addRenderableWidget(new AETextField(style, font, leftPos + 164, topPos + TRANSFER_NAME_Y, 128, 12));
        transferName.setBordered(false);
        transferName.setMaxLength(64);
        transferName.setPlaceholder(Component.translatable("screen.ae2security.transfer_name"));
        transferName.setMessage(Component.translatable("screen.ae2security.transfer_name"));
        transferName.setValue(transferInput);
        transferName.setResponder(value -> {
            transferInput = value;
            refreshControls();
        });
        transferButton = ae2Button(164, TRANSFER_BUTTON_Y, 128, 18, Component.empty(), this::transferOwnership);
        transferButton.setTooltip(Tooltip.create(Component.translatable("screen.ae2security.transfer_warning")));

        displayed = menu.snapshot();
        if (displayed != null) players.refresh(true);
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
            case COLLECT -> Icon.AUTO_EXPORT_ON;
            case BUILD -> Icon.PLACEMENT_ITEM;
        };
    }

    private void turnPage(int delta) {
        players.turnPage(delta);
        clearTransferConfirmation();
        refreshControls();
    }

    private void selectVisiblePlayer(int row) {
        if (!players.select(row)) {
            return;
        }
        clearTransferConfirmation();
        refreshControls();
    }

    private void togglePermission(Permission permission) {
        send(EditPolicy.Action.TOGGLE_PERMISSION, permission.bit());
    }

    private void toggleTrust() {
        var player = players.selection();
        if (player != null) {
            send(player.trusted() ? EditPolicy.Action.REMOVE : EditPolicy.Action.TRUST, Permission.VIEW.bit());
        }
    }

    private void transferOwnership() {
        var player = players.selection();
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

        boolean pending = edits.pending();
        boolean searchPending = players.searching();
        var visiblePlayers = players.players();
        var player = players.selection();
        for (int row = 0; row < PAGE_SIZE; row++) {
            var button = playerButtons[row];
            if (row >= visiblePlayers.size()) {
                button.visible = false;
                button.active = false;
                continue;
            }

            var listed = visiblePlayers.get(row);
            String marker = listed.trusted() ? "+ " : "  ";
            button.setMessage(Component.literal(marker + font.plainSubstrByWidth(listed.name(), 116)));
            button.setTooltip(Tooltip.create(SecurityScreenText.playerTooltip(listed)));
            button.setSelected(player != null && listed.id().equals(player.id()));
            button.visible = true;
            button.active = true;
        }

        previousPageButton.active = !searchPending && players.page() > 0;
        nextPageButton.active = !searchPending && players.page() < players.lastPage();

        boolean trusted = player != null && player.trusted();
        for (var permission : Permission.editable()) {
            boolean enabled = player != null && permission.in(player.permissions());
            String permissionKey = "permission.ae2security." + permission.name().toLowerCase(Locale.ROOT);
            var button = permissionButtons.get(permission);
            var name = Component.translatable(permissionKey);
            var description = Component.translatable(permissionKey + ".description");
            button.setSelected(enabled);
            button.visible = trusted;
            button.active = trusted && !pending && !searchPending;
            button.setTooltip(Tooltip.create(Component.empty()
                    .append(Component.translatable(
                            enabled ? "screen.ae2security.permission_enabled"
                                    : "screen.ae2security.permission_disabled",
                            name))
                    .append("\n")
                    .append(description)));
        }

        trustButton.setMessage(Component.translatable(pending
                ? "screen.ae2security.pending"
                : trusted ? "screen.ae2security.remove" : "screen.ae2security.trust"));
        trustButton.setY(topPos + (trusted ? TRUST_BUTTON_Y : TRUST_BUTTON_Y_UNTRUSTED));
        trustButton.visible = player != null;
        trustButton.active = player != null && !pending && !searchPending;

        boolean confirming = trusted && player.id().equals(transferConfirmation);
        transferName.visible = confirming;
        transferName.active = confirming && !pending && !searchPending;
        transferButton.setMessage(Component.translatable(
                confirming ? "screen.ae2security.confirm_transfer" : "screen.ae2security.transfer"));
        transferButton.visible = trusted;
        transferButton.active = trusted && !pending && !searchPending
                && (!confirming || OwnershipConfirmation.matches(player.name(), transferInput));
    }

    private void send(EditPolicy.Action action, int mask) {
        send(action, mask, "");
    }

    private void send(EditPolicy.Action action, int mask, String confirmation) {
        var snapshot = menu.snapshot();
        var player = players.selection();
        if (snapshot == null || player == null) {
            return;
        }

        // Only permission toggles may overlap each other. Nothing is sent while a trust,
        // removal or transfer is unacknowledged: the menu keeps one result, so a second
        // acknowledgement could replace the one this screen is waiting for.
        int requestId = edits.begin(action != EditPolicy.Action.TOGGLE_PERMISSION);
        if (requestId == PendingEdits.BLOCKED) {
            return;
        }
        PacketDistributor.sendToServer(new EditPolicy(menu.containerId, requestId, snapshot.revision(), player.id(),
                action, mask, confirmation));
        refreshControls();
    }

    private void clearTransferConfirmation() {
        transferConfirmation = null;
        transferInput = "";
        if (transferName != null) transferName.setValue("");
        if (transferName != null) transferName.setFocused(false);
        if (getFocused() == transferName) setFocused(null);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (displayed != menu.snapshot()) {
            var old = displayed;
            displayed = menu.snapshot();
            if (old == null || displayed == null || !old.owner().equals(displayed.owner()))
                clearTransferConfirmation();
            if (displayed != null && (old == null
                    || old.revision() != displayed.revision()
                    || old.directoryRevision() != displayed.directoryRevision()))
                players.refresh(false);
            refreshControls();
        }
        var edit = edits.accept(menu.editResult());
        if (edit != PendingEdits.Outcome.IGNORED) {
            // A lost acknowledgement leaves the outcome unknown, so reload what the server holds.
            if (edit == PendingEdits.Outcome.SUPERSEDED) players.refresh(false);
            refreshControls();
        }
        var request = menu.snapshot() == null ? null : players.poll(menu.containerId);
        if (request != null) PacketDistributor.sendToServer(request);
        var update = players.accept(menu.searchResult());
        if (update != PlayerListState.Update.IGNORED) {
            if (update == PlayerListState.Update.SELECTION_STALE) clearTransferConfirmation();
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
        drawInsetPanel(graphics, x + 7, y + 38, 151, 188);
        drawInsetPanel(graphics, x + 159, y + 38, 138, 188);
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

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 9, AE2_DARK, false);

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

        var selectedPlayer = players.selection();
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

        // The prompt and the edit status share one line; the status wins while an edit is pending.
        if (selectedPlayer != null && selectedPlayer.id().equals(transferConfirmation) && !edits.pending()) {
            var prompt = Component.translatable("screen.ae2security.transfer_prompt", selectedPlayer.name());
            graphics.drawString(font, font.plainSubstrByWidth(prompt.getString(), 128), 164, TRANSFER_TEXT_Y,
                    AE2_MUTED_TEXT, false);
        }

        String pages = (players.page() + 1) + " / " + (players.lastPage() + 1);
        graphics.drawCenteredString(font, pages, 82, PAGER_Y + 5, AE2_DARK);
        if (players.players().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(players.searching()
                    ? "screen.ae2security.searching" : "screen.ae2security.no_players"), 82, 98,
                    AE2_MUTED_TEXT);
        }
        boolean pending = edits.pending();
        var editStatus = edits.status();
        if (pending || editStatus != null && transferConfirmation == null) {
            var status = Component.translatable(pending ? "screen.ae2security.pending"
                    : "screen.ae2security.edit_result." + editStatus.name().toLowerCase(Locale.ROOT));
            graphics.drawString(font, font.plainSubstrByWidth(status.getString(), 128),
                    164, TRANSFER_TEXT_Y, editStatus == PolicyEditResult.Result.APPLIED ? AE2_DARK : STATUS_WARNING, false);
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

    /** Fills AE2's 128px texture middle while retaining the full editor width. */
    private static final class FullWidthSearchField extends AETextField {
        FullWidthSearchField(ScreenStyle style, Font font, int x, int y, int width, int height) {
            super(style, font, x, y, width, height);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (visible) {
                var area = getTooltipArea();
                int extension = area.getWidth() - AE_TEXT_FIELD_TEXTURE_SIZE;
                if (extension > 0) graphics.blit(AE_TEXT_FIELD_TEXTURE,
                        area.getX() + 127, area.getY(), extension, 12,
                        1, isFocused() ? 24 : 0, 1, 12,
                        AE_TEXT_FIELD_TEXTURE_SIZE, AE_TEXT_FIELD_TEXTURE_SIZE);
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
            int top = isHovered() ? y : y - 1;
            graphics.fill(x - 1, top, right + 1, top + 1, AE2_ACCENT);
            graphics.fill(x - 1, bottom, right + 1, bottom + 1, AE2_ACCENT);
            graphics.fill(x - 1, y, x, bottom, AE2_ACCENT);
            graphics.fill(right, y, right + 1, bottom, AE2_ACCENT);
        }
    }
}
