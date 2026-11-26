package dev.ae2security.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import appeng.client.Point;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.OpenGuideButton;
import appeng.client.gui.widgets.VerticalButtonBar;
import dev.ae2security.MESecurity;
import dev.ae2security.menu.SecurityMenu;
import dev.ae2security.network.EditPolicy;
import dev.ae2security.network.PlayerSearchResult;
import dev.ae2security.network.PolicySnapshot;
import dev.ae2security.security.OwnershipConfirmation;
import dev.ae2security.security.Permission;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The owner's management screen, laid out like an AE2 terminal: a framed AE2 background with
 * the guide in AE2's left toolbar, a searchable player list with AE2's scrollbar (one notch
 * per page of server results), and the selected player's access on the right.
 */
public final class SecurityScreen extends AbstractContainerScreen<SecurityMenu> {
    private static final int PAGE_SIZE = PlayerSearchResult.PAGE_SIZE;

    private final ScreenStyle style;
    private final PlayerRowButton[] playerRows = new PlayerRowButton[PAGE_SIZE];
    private final Map<Permission, PermissionSwitch> permissionSwitches = new EnumMap<>(Permission.class);
    private final Rect2i searchBox;
    private final Rect2i listBox;
    private final Rect2i scrollBox;
    private final Rect2i details;

    private AETextField search;
    private AETextField transferName;
    private AE2Button trustButton;
    private TransferTabButton transferTab;
    private TransferPopup popup;
    private PlayerPageScroll pageScroll;
    private AccessPanel access;
    private VerticalButtonBar toolbar;

    private final PlayerListState players = new PlayerListState();
    private final PendingEdits edits = new PendingEdits();
    private UUID transferConfirmation;
    private String transferInput = "";
    /** Whether the open popup sent a transfer, so the edit status it shows is its own. */
    private boolean transferRequested;
    /** A transfer's result belongs to the popup, not to the access panel. */
    private boolean lastEditWasTransfer;
    private PolicySnapshot displayed;

    public SecurityScreen(SecurityMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.style = StyleManager.loadStyleDoc("/screens/ae2security/security_terminal.json");
        var frame = style.getGeneratedBackground();
        imageWidth = frame.getWidth();
        imageHeight = frame.getHeight();
        searchBox = SecurityGui.box(style, "search", imageWidth, imageHeight);
        listBox = SecurityGui.box(style, "playerList", imageWidth, imageHeight);
        scrollBox = SecurityGui.box(style, "playerScrollbar", imageWidth, imageHeight);
        details = SecurityGui.box(style, "details", imageWidth, imageHeight);
    }

    @Override
    protected void init() {
        super.init();

        var help = new OpenGuideButton(button -> {
            if (minecraft != null && minecraft.player != null) {
                GuidesCommon.openGuide(minecraft.player, ResourceLocation.parse("ae2:guide"),
                        PageAnchor.page(MESecurity.id("security_terminal.md")));
            }
        });
        help.setTooltip(Tooltip.create(Component.translatable("screen.ae2security.guide")));
        transferTab = new TransferTabButton(button -> openTransferPopup());
        toolbar = new VerticalButtonBar();
        toolbar.add(help);
        toolbar.add(transferTab);
        toolbar.setPosition(style.getWidget("verticalToolbar").resolve(new Rect2i(0, 0, imageWidth, imageHeight)));
        toolbar.populateScreen(this::addRenderableWidget, new Rect2i(leftPos, topPos, imageWidth, imageHeight), null);
        toolbar.updateBeforeRender();

        search = addRenderableWidget(new FullWidthSearchField(style, font, leftPos + searchBox.getX(),
                topPos + searchBox.getY(), searchBox.getWidth(), searchBox.getHeight()));
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
            playerRows[row] = addRenderableWidget(new PlayerRowButton(style, leftPos + listBox.getX() + 1,
                    topPos + listBox.getY() + 1 + row * (PlayerRowButton.HEIGHT + 1), listBox.getWidth() - 2,
                    button -> selectVisiblePlayer(rowIndex)));
        }

        pageScroll = new PlayerPageScroll(scrollBox, listBox);

        access = new AccessPanel(style, font, details);
        int left = leftPos + access.left();
        int width = access.width();
        for (int row = 0; row < Permission.editable().size(); row++) {
            var permission = Permission.editable().get(row);
            var toggle = new PermissionSwitch(style, PermissionSwitch.icon(permission),
                    Component.translatable(PermissionSwitch.key(permission)), left, topPos + access.permissionY(row),
                    width, () -> togglePermission(permission));
            permissionSwitches.put(permission, addRenderableWidget(toggle));
        }

        trustButton = addRenderableWidget(new AE2Button(left, topPos + access.trustY(), width,
                AccessPanel.BUTTON_HEIGHT, Component.empty(), button -> toggleTrust()));
        // The popup's widgets are not children of the screen: it draws them over the dimmed
        // screen and receives all input itself while it is open.
        popup = new TransferPopup(style, font, this.width, this.height, this::transferOwnership, this::cancelTransfer);
        transferName = popup.field;
        transferName.setValue(transferInput);
        transferName.setResponder(value -> {
            transferInput = value;
            if (!edits.pending()) transferRequested = false; // editing brings the prompt back
            refreshControls();
        });

        displayed = menu.snapshot();
        if (displayed != null) players.refresh(true);
        refreshControls();
        setInitialFocus(search);
    }

    private void turnPage(int delta) {
        players.turnPage(delta);
        clearTransferConfirmation();
        refreshControls();
    }

    /** The scrollbar moves one notch per page; follow it once the owner has moved it. */
    private void followScrollbar() {
        int target = pageScroll.page();
        if (menu.snapshot() != null && target != players.page()) turnPage(target - players.page());
    }

    private void selectVisiblePlayer(int row) {
        if (!players.select(row)) {
            return;
        }
        clearTransferConfirmation();
        refreshControls();
    }

    /**
     * While an edit is unacknowledged or the list is reloading, the selection may be stale, so
     * clicks are ignored. The controls keep their normal look meanwhile: greying them out for
     * the few ticks of each round trip made the whole column flicker on every click.
     */
    private boolean busy() {
        return edits.pending() || players.searching();
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

    /** The toolbar tab: opens the confirmation for the selected trusted player. */
    private void openTransferPopup() {
        var player = players.selection();
        if (player == null || !player.trusted() || busy()) {
            return;
        }
        transferConfirmation = player.id();
        transferInput = "";
        transferRequested = false;
        transferName.setValue("");
        refreshControls();
        setFocused(transferName);
    }

    /** The popup's Confirm: the same typed-name check and TRANSFER edit as before. */
    private void transferOwnership() {
        var player = players.selection();
        if (player == null || !player.id().equals(transferConfirmation)) {
            return;
        }
        if (OwnershipConfirmation.matches(player.name(), transferInput)) {
            transferRequested = true;
            send(EditPolicy.Action.TRANSFER, 0, transferInput);
        } else if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "message.ae2security.transfer_name_mismatch", player.name()), true);
            setFocused(transferName);
        }
    }

    private void cancelTransfer() {
        clearTransferConfirmation();
        refreshControls();
    }

    /** The popup is open while the selected, still trusted player awaits confirmation. */
    private boolean popupOpen() {
        var player = players.selection();
        return popup != null && player != null && player.trusted() && player.id().equals(transferConfirmation);
    }

    private void refreshControls() {
        if (playerRows[0] == null) {
            return;
        }

        boolean pending = edits.pending();
        boolean searchPending = players.searching();
        var visiblePlayers = players.players();
        var player = players.selection();
        for (int row = 0; row < PAGE_SIZE; row++) {
            var button = playerRows[row];
            if (row >= visiblePlayers.size()) {
                button.visible = false;
                button.active = false;
                continue;
            }

            var listed = visiblePlayers.get(row);
            button.show(listed, player != null && listed.id().equals(player.id()));
            button.setTooltip(Tooltip.create(SecurityScreenText.playerTooltip(listed)));
            button.visible = true;
            button.active = true;
        }

        pageScroll.show(players.page(), players.lastPage(), searchPending);

        boolean trusted = player != null && player.trusted();
        for (var permission : Permission.editable()) {
            boolean enabled = player != null && permission.in(player.permissions());
            var button = permissionSwitches.get(permission);
            var name = Component.translatable(PermissionSwitch.key(permission));
            var description = Component.translatable(PermissionSwitch.key(permission) + ".description");
            button.setSelected(enabled);
            button.visible = trusted;
            button.active = trusted;
            button.setTooltip(Tooltip.create(Component.empty()
                    .append(Component.translatable(
                            enabled ? "screen.ae2security.permission_enabled"
                                    : "screen.ae2security.permission_disabled",
                            name))
                    .append("\n")
                    .append(description.withStyle(ChatFormatting.GRAY))));
        }

        trustButton.setMessage(Component.translatable(
                trusted ? "screen.ae2security.remove" : "screen.ae2security.trust"));
        trustButton.visible = player != null;
        trustButton.active = player != null;

        // Only trusted players can receive ownership.
        transferTab.active = trusted;
        transferTab.setTooltip(Tooltip.create(Component.translatable("screen.ae2security.transfer").append("\n")
                .append(Component.translatable(trusted ? "screen.ae2security.transfer_warning"
                        : "screen.ae2security.transfer_needs_trusted").withStyle(ChatFormatting.GRAY))));
        boolean confirming = popupOpen();
        transferName.active = confirming && !pending && !searchPending;
        popup.confirm.active = confirming && !pending && !searchPending
                && OwnershipConfirmation.matches(player.name(), transferInput);
    }

    private void send(EditPolicy.Action action, int mask) {
        send(action, mask, "");
    }

    private void send(EditPolicy.Action action, int mask, String confirmation) {
        var snapshot = menu.snapshot();
        var player = players.selection();
        if (snapshot == null || player == null || players.searching()) {
            return;
        }

        // Only permission toggles may overlap each other. Nothing is sent while a trust,
        // removal or transfer is unacknowledged: the menu keeps one result, so a second
        // acknowledgement could replace the one this screen is waiting for.
        int requestId = edits.begin(action != EditPolicy.Action.TOGGLE_PERMISSION);
        if (requestId == PendingEdits.BLOCKED) {
            return;
        }
        lastEditWasTransfer = action == EditPolicy.Action.TRANSFER;
        PacketDistributor.sendToServer(new EditPolicy(menu.containerId, requestId, snapshot.revision(), player.id(),
                action, mask, confirmation));
        refreshControls();
    }

    private void clearTransferConfirmation() {
        transferConfirmation = null;
        transferInput = "";
        transferRequested = false;
        transferName.setValue("");
        transferName.setFocused(false);
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
        pageScroll.tick();
        followScrollbar();
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
        if (popupOpen()) {
            var action = popup.keyPressed(keyCode, scanCode, modifiers);
            if (action == TransferPopup.KeyAction.CANCEL) cancelTransfer();
            if (action == TransferPopup.KeyAction.CONFIRM) transferOwnership();
            // Nothing behind the popup sees keys; the inventory key types into its field.
            return true;
        }
        if ((search != null && search.isFocused() || transferName != null && transferName.isFocused())
                && minecraft != null
                && minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            // Inventory keys are configurable. When the focused field receives that character,
            // keep the screen open and let the matching charTyped event enter it normally.
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Point local(double mouseX, double mouseY) {
        return new Point((int) mouseX - leftPos, (int) mouseY - topPos);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!popupOpen()) return super.charTyped(codePoint, modifiers);
        transferName.charTyped(codePoint, modifiers);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (popupOpen()) {
            popup.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (pageScroll.mouseClicked(local(mouseX, mouseY), button)) {
            followScrollbar();
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // A clicked button keeps keyboard focus, and focus draws the hover look, so a switch
        // stayed highlighted after the mouse left it. Only keyboard navigation should focus it.
        if (getFocused() instanceof AbstractButton) setFocused(null);
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (popupOpen()) return true;
        if (pageScroll.mouseDragged(local(mouseX, mouseY), button)) {
            followScrollbar();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (popupOpen()) return popup.mouseReleased(mouseX, mouseY, button);
        pageScroll.mouseReleased(local(mouseX, mouseY), button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popupOpen()) return true;
        if (pageScroll.mouseScrolled(local(mouseX, mouseY), scrollY)) {
            followScrollbar();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        BackgroundGenerator.draw(imageWidth, imageHeight, graphics, leftPos, topPos);
        // Like AEBaseScreen: the toolbar tab is drawn over the frame's left edge.
        toolbar.drawBackgroundLayer(graphics, new Rect2i(leftPos, topPos, imageWidth, imageHeight),
                new Point(mouseX - leftPos, mouseY - topPos));
        SecurityGui.well(graphics, style, leftPos + listBox.getX(), topPos + listBox.getY(),
                listBox.getWidth(), listBox.getHeight());
        SecurityGui.well(graphics, style, leftPos + scrollBox.getX(), topPos + scrollBox.getY(),
                scrollBox.getWidth(), scrollBox.getHeight());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int text = SecurityGui.color(style, PaletteColor.DEFAULT_TEXT_COLOR);
        int muted = SecurityGui.color(style, PaletteColor.MUTED_TEXT_COLOR);
        SecurityGui.text(graphics, font, title, 8, 6, text);
        pageScroll.draw(graphics, new Rect2i(0, 0, imageWidth, imageHeight),
                new Point(mouseX - leftPos, mouseY - topPos));

        var snapshot = menu.snapshot();
        if (snapshot == null) {
            SecurityGui.text(graphics, font, Component.translatable("screen.ae2security.loading"), 8, 17, muted);
            SecurityGui.centeredIn(graphics, style, font, listBox, Component.translatable("screen.ae2security.loading"));
            return;
        }

        SecurityGui.networkWarning(graphics, style, font, snapshot.status(), imageWidth - 8, 6);
        int ownerWidth = imageWidth - 16;
        SecurityGui.text(graphics, font, font.plainSubstrByWidth(
                Component.translatable("screen.ae2security.owner", snapshot.ownerName()).getString(), ownerWidth),
                8, 17, muted);

        if (players.players().isEmpty()) {
            SecurityGui.centeredIn(graphics, style, font, listBox, Component.translatable(players.searching()
                    ? "screen.ae2security.searching" : "screen.ae2security.no_players"));
        }
        int footerY = listBox.getY() + listBox.getHeight() + 3;
        String pages = (players.page() + 1) + " / " + (players.lastPage() + 1);
        int pagesX = scrollBox.getX() + scrollBox.getWidth() - font.width(pages);
        SecurityGui.text(graphics, font, pages, pagesX, footerY, muted);
        // The access panel has no name heading; its player is the highlighted row. When that
        // row is on another page (trusting re-sorts the list), the footer names them instead.
        var selected = players.selection();
        if (selected != null && !SecurityScreenText.isListed(players.players(), selected)) {
            int width = pagesX - listBox.getX() - 5;
            String label = SecurityScreenText.firstThatFits(List.of(
                    Component.translatable("screen.ae2security.selected_elsewhere", selected.name()).getString(),
                    selected.name()), width, font::width);
            SecurityGui.text(graphics, font, font.plainSubstrByWidth(label, width), listBox.getX() + 1, footerY, text);
        } else {
            SecurityGui.text(graphics, font, Component.translatable("screen.ae2security.players"),
                    listBox.getX() + 1, footerY, muted);
        }

        access.draw(graphics, players.selection(), lastEditWasTransfer ? null : edits.status(), popupOpen());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        toolbar.updateBeforeRender();
        boolean open = popupOpen();
        // Under the popup nothing is hovered, so nothing highlights or shows a tooltip.
        int screenMouseX = open ? -1 : mouseX;
        int screenMouseY = open ? -1 : mouseY;
        super.render(graphics, screenMouseX, screenMouseY, partialTick);
        renderTooltip(graphics, screenMouseX, screenMouseY);
        if (open) {
            popup.render(graphics, width, height, players.selection().name(), edits.pending(),
                    transferRequested ? edits.status() : null, mouseX, mouseY, partialTick);
        }
    }
}
