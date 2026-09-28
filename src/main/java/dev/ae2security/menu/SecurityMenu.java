package dev.ae2security.menu;

import java.util.*;
import appeng.api.parts.PartHelper;
import dev.ae2security.MESecurity;
import dev.ae2security.network.*;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** No item slots: all access editing is validated against the live server block and policy revision. */
public final class SecurityMenu extends AbstractContainerMenu {
    private final Player player;
    private final BlockPos position;
    private final Direction side;
    private final UUID terminalId;
    private long sentRevision = -1;
    private long sentDirectoryRevision = -1;
    private String sentStatus = "";
    private PolicySnapshot snapshot;
    private PlayerSearchResult searchResult;
    private PolicyEditResult editResult;
    private long lastSearchAt;

    public SecurityMenu(int id, Inventory inventory, BlockPos position, Direction side) {
        this(id, inventory, position, side, null);
    }
    public SecurityMenu(int id, Inventory inventory, SecurityTerminalPart terminal) {
        this(id, inventory, terminal.getBlockEntity().getBlockPos(), terminal.getSide(), terminal.terminalId());
    }
    private SecurityMenu(int id, Inventory inventory, BlockPos position, Direction side, UUID terminalId) {
        super(MESecurity.SECURITY_MENU.get(), id);
        this.player = inventory.player;
        this.position = position.immutable();
        this.side = side;
        this.terminalId = terminalId;
    }

    public PolicySnapshot snapshot() { return snapshot; }
    public void acceptSnapshot(PolicySnapshot snapshot) {
        if (player.level().isClientSide() && snapshot.menuId() == containerId) this.snapshot = snapshot;
    }
    public PlayerSearchResult searchResult() { return searchResult; }
    public PolicyEditResult editResult() { return editResult; }
    public void acceptSearchResult(PlayerSearchResult result) {
        if (player.level().isClientSide() && result.menuId() == containerId) searchResult = result;
    }
    public void acceptEditResult(PolicyEditResult result) {
        if (player.level().isClientSide() && result.menuId() == containerId) editResult = result;
    }

    private SecurityTerminalPart terminal() {
        if (!player.level().getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) return null;
        var part = PartHelper.getPart(player.level(), position, side);
        return part instanceof SecurityTerminalPart terminal
                && (terminalId == null || terminalId.equals(terminal.terminalId())) ? terminal : null;
    }

    @Override public boolean stillValid(Player viewer) {
        if (viewer != player || viewer.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) > 64) return false;
        if (viewer.level().isClientSide()) return true;
        var terminal = terminal();
        if (terminal == null) return false;
        var policy = SecuritySavedData.get(viewer.getServer()).policy(terminal.terminalId());
        return policy != null && policy.isOwner(viewer.getUUID());
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!stillValid(player)) {
            serverPlayer.closeContainer();
            return;
        }
        var data = SecuritySavedData.get(serverPlayer.server);
        var policy = data.policy(terminalId);
        var grid = SecurityAccess.grid(terminal());
        String status = grid == null ? "disconnected" : grid.getService(ISecurityGrid.class).terminalIds().size() > 1 ? "conflict" : "protected";
        if (policy.revision() == sentRevision && data.directoryRevision() == sentDirectoryRevision && status.equals(sentStatus)) return;
        PacketDistributor.sendToPlayer(serverPlayer, new PolicySnapshot(containerId, policy.owner(), data.name(policy.owner()),
                policy.revision(), status, data.directoryRevision()));
        sentRevision = policy.revision();
        sentDirectoryRevision = data.directoryRevision();
        sentStatus = status;
    }

    public void search(Player actor, PlayerSearchRequest request) {
        if (!(actor instanceof ServerPlayer serverPlayer) || actor != player || actor.containerMenu != this
                || request.menuId() != containerId || !stillValid(actor)) return;
        if (request.page() < 0 || request.query().length() > 64) return;
        long now = System.nanoTime();
        if (lastSearchAt != 0 && now - lastSearchAt < 100_000_000L) {
            PacketDistributor.sendToPlayer(serverPlayer, new PlayerSearchResult(containerId,
                    request.sequence(), request.page(), 0, true, List.of()));
            return;
        }
        lastSearchAt = now;
        var data = SecuritySavedData.get(serverPlayer.server);
        var policy = data.policy(terminalId);
        if (policy == null) return;
        PacketDistributor.sendToPlayer(serverPlayer, PlayerDirectorySearch.search(containerId,
                request.sequence(), policy.owner(), data.players(), policy, request.query(), request.page()));
    }

    private void acknowledge(ServerPlayer player, EditPolicy edit, PolicyEditResult.Result result) {
        PacketDistributor.sendToPlayer(player, new PolicyEditResult(containerId, edit.requestId(), result));
    }

    public void edit(Player actor, EditPolicy edit) {
        if (!(actor instanceof ServerPlayer serverPlayer) || actor != player || actor.containerMenu != this) return;
        if (!stillValid(actor)) {
            acknowledge(serverPlayer, edit, PolicyEditResult.Result.DENIED);
            serverPlayer.closeContainer();
            return;
        }
        var data = SecuritySavedData.get(serverPlayer.server);
        var policy = data.policy(terminalId);
        boolean toggle = edit.action() == EditPolicy.Action.TOGGLE_PERMISSION;
        // A toggle is an ordered intent, not a replacement snapshot. Apply each click to
        // the live policy so a slow round trip cannot lose another permission change.
        boolean invalidRevision = toggle ? edit.revision() > policy.revision() : policy.revision() != edit.revision();
        boolean invalidToggle = toggle && Permission.editable().stream().noneMatch(p -> p.bit() == edit.permissions());
        if (invalidRevision || invalidToggle || edit.permissions() < 0 || edit.permissions() > Permission.ALL) {
            acknowledge(serverPlayer, edit, PolicyEditResult.Result.REFRESH);
            sentRevision = -1;
            broadcastChanges();
            return;
        }
        if (edit.target().equals(policy.owner()) || !data.players().containsKey(edit.target()) && !policy.trusted().containsKey(edit.target())) {
            acknowledge(serverPlayer, edit, PolicyEditResult.Result.INVALID);
            return;
        }
        if (edit.action() == EditPolicy.Action.TRANSFER
                && !OwnershipConfirmation.matches(data.name(edit.target()), edit.confirmation())) {
            actor.sendSystemMessage(Component.translatable("message.ae2security.transfer_name_mismatch",
                    data.name(edit.target())));
            acknowledge(serverPlayer, edit, PolicyEditResult.Result.INVALID);
            sentRevision = -1;
            broadcastChanges();
            return;
        }
        var updated = switch (edit.action()) {
            case TOGGLE_PERMISSION -> policy.trusted().containsKey(edit.target())
                    ? policy.trust(actor.getUUID(), edit.target(), policy.permissions(edit.target()) ^ edit.permissions())
                    : policy;
            case TRUST -> policy.trust(actor.getUUID(), edit.target(), Permission.VIEW.bit());
            case PERMISSIONS -> policy.trusted().containsKey(edit.target()) ? policy.trust(actor.getUUID(), edit.target(), edit.permissions()) : policy;
            case REMOVE -> policy.remove(actor.getUUID(), edit.target());
            case TRANSFER -> policy.transfer(actor.getUUID(), edit.target());
        };
        if (updated == policy) {
            acknowledge(serverPlayer, edit, PolicyEditResult.Result.REFRESH);
            sentRevision = -1;
            broadcastChanges();
            return;
        }
        data.update(terminalId, updated);
        SecurityAccess.policyChanged(serverPlayer.server, terminalId, policy, updated);
        acknowledge(serverPlayer, edit, PolicyEditResult.Result.APPLIED);
        if (!updated.isOwner(actor.getUUID())) {
            actor.sendSystemMessage(Component.translatable("message.ae2security.transferred", data.name(updated.owner())));
            serverPlayer.closeContainer();
        } else broadcastChanges();
    }
}
