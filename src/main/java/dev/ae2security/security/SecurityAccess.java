package dev.ae2security.security;

import java.util.*;
import appeng.api.networking.*;
import appeng.api.networking.security.*;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IPatternAccessTermMenuHost;
import appeng.api.parts.IPartHost;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.contents.NetworkToolMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.crafting.*;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** The common server authorization entry point for menus, storage, crafting and shortcuts. */
public final class SecurityAccess {
    private static final Map<Player, Long> LAST_NOTICE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> STORAGE_MENUS = Set.of("item_terminal", "craftingterm", "wirelessterm",
            "wirelesscraftingterm", "basic_cell_chest", "portable_item_cell", "portable_fluid_cell");
    private static final Set<String> HARDWARE_MENUS = Set.of("drive", "me_chest", "ioport", "priority",
            "storagebus", "import_bus", "export_bus", "formationplane", "interface", "set_stock_amount",
            "molecular_assembler", "energy_level_emitter", "storage_level_emitter", "qnb", "spatialanchor",
            "spatialioport", "wireless_access_point", "vibrationchamber", "inscriber", "condenser");
    /** AE2 Wireless Terminals menus that only need View; trash and magnet hold the player's own item data. */
    private static final Set<String> WIRELESS_VIEW_MENUS = Set.of("wireless_crafting_terminal", "trash", "magnet");
    private SecurityAccess() {}

    public static IGrid grid(Object host) {
        IGridNode node = null;
        if (host instanceof IActionHost actionHost) node = actionHost.getActionableNode();
        if (node == null && host instanceof IPatternAccessTermMenuHost patternHost) node = patternHost.getGridNode();
        if (node != null) return node.getGrid();
        if (host instanceof WirelessTerminalMenuHost<?> wireless && !wireless.isClientSide()) {
            return wireless.getItem().getLinkedGrid(wireless.getItemStack(), wireless.getPlayer().level(), null);
        }
        // The Network Tool's status screen reports on the clicked host; same lookup order as NetworkStatusMenu.
        if (host instanceof NetworkToolMenuHost<?> tool && tool.getGridHost() != null) {
            for (var side : net.minecraft.core.Direction.values()) {
                var grid = gridOf(tool.getGridHost().getGridNode(side));
                if (grid != null) return grid;
            }
        }
        return null;
    }

    /** A node that is being torn down has no grid; AE2 reports that by throwing. */
    private static IGrid gridOf(IGridNode node) {
        if (node == null) return null;
        try {
            return node.getGrid();
        } catch (IllegalStateException destroyed) {
            return null;
        }
    }

    public static AccessDecision check(IGrid grid, Player player, Permission permission, boolean ownerOnly) {
        if (grid == null || player.level().isClientSide()) return AccessDecision.ALLOW;
        return grid.getService(ISecurityGrid.class).check(player.getUUID(), permission, ownerOnly);
    }

    public static boolean allow(IGrid grid, Player player, Permission permission, boolean ownerOnly) {
        var decision = check(grid, player, permission, ownerOnly);
        if (decision != AccessDecision.ALLOW) notice(player, decision);
        return decision == AccessDecision.ALLOW;
    }

    public static boolean transfer(IGrid grid, IActionSource source, Permission permission) {
        if (grid == null) throw new IllegalStateException("AE2 network storage is missing its security grid binding");
        var player = source.player().orElse(null);
        if (player == null) return machineTransfer(grid, source);
        if (source instanceof CraftingActionSource crafting && crafting.grid() == grid) {
            return allow(grid, player, Permission.CRAFT, false);
        }
        return allow(grid, player, permission, false);
    }

    /**
     * Machine automation has no player principal and is unrestricted on its own grid. A machine acting from
     * another grid (a subnet reaching in through an Interface) is judged by the player who placed it, because
     * linking networks is a Build-level act. Sources without a resolvable machine node keep AE2's behavior.
     */
    private static boolean machineTransfer(IGrid grid, IActionSource source) {
        var machine = source.machine().orElse(null);
        var node = machine == null ? null : machine.getActionableNode();
        var origin = gridOf(node);
        if (origin == null || origin == grid) return true;
        var security = grid.getService(ISecurityGrid.class);
        if (security.terminalIds().isEmpty()) return true;
        var placer = node.getOwningPlayerProfileId();
        return placer != null && security.check(placer, Permission.BUILD, false) == AccessDecision.ALLOW;
    }

    /**
     * Mount-time rule for a storage bus: native AE2 hardware of another protected network (an Interface's
     * whole-network storage, an ME Chest's cell) is only mounted when the bus's placer has Build there.
     * UNRESOLVED means the faced hardware has no grid yet and the caller should look again later.
     * A mount that was legitimate stays mounted until the bus re-evaluates its target; {@link #transfer}
     * still stops Interface transfers live once the placer loses Build.
     */
    public static AccessDecision adjacentStorage(appeng.parts.AEBasePart bus) {
        var level = bus.getLevel();
        if (level == null || level.isClientSide()) return AccessDecision.ALLOW;
        var side = bus.getSide();
        var target = side == null ? null : level.getBlockEntity(bus.getBlockEntity().getBlockPos().relative(side));
        if (!SecurityHardware.nativeHost(target)) return AccessDecision.ALLOW;
        var faced = target instanceof IPartHost parts ? parts.getPart(side.getOpposite()) : null;
        Collection<IGrid> grids;
        if (faced != null) {
            // Only the part that provides the storage matters, not unrelated parts sharing the block.
            grids = new ArrayList<>(2);
            var internal = gridOf(faced.getGridNode());
            if (internal == null) return AccessDecision.UNRESOLVED;
            grids.add(internal);
            var external = gridOf(faced.getExternalFacingNode());
            if (external != null) grids.add(external);
        } else {
            var networks = SecurityHardware.networks(target);
            if (networks.unresolved) return AccessDecision.UNRESOLVED;
            grids = networks.grids;
        }
        var node = bus.getGridNode();
        var own = gridOf(node);
        var placer = own == null ? null : node.getOwningPlayerProfileId();
        for (var grid : grids) {
            if (grid == own) continue;
            var security = grid.getService(ISecurityGrid.class);
            if (security.terminalIds().isEmpty()) continue;
            if (placer == null) return AccessDecision.DENIED;
            // A missing policy or terminal conflict is a refusal here, not a reason to keep polling.
            if (security.check(placer, Permission.BUILD, false) != AccessDecision.ALLOW) return AccessDecision.DENIED;
        }
        return AccessDecision.ALLOW;
    }

    /** Uses the same terminal identity across saves and grid rebuilds, never a neighboring grid's allowance. */
    public static UUID craftingTerminal(IGrid grid) {
        if (grid == null) return null;
        var ids = grid.getService(ISecurityGrid.class).terminalIds();
        return ids.size() == 1 ? ids.iterator().next() : null;
    }

    public static long extractionLimit(IGrid grid, IActionSource source, AEKey key, long amount) {
        if (grid == null) throw new IllegalStateException("AE2 network storage is missing its security grid binding");
        if (amount <= 0) return 0;
        var player = source.player().orElse(null);
        if (player == null || source instanceof CraftingActionSource) {
            return transfer(grid, source, Permission.EXTRACT) ? amount : 0;
        }
        var decision = check(grid, player, Permission.EXTRACT, false);
        if (decision == AccessDecision.ALLOW) return amount;
        var terminal = craftingTerminal(grid);
        if (terminal != null && player instanceof ServerPlayer serverPlayer
                && check(grid, player, Permission.COLLECT, false) == AccessDecision.ALLOW) {
            long available = SecuritySavedData.get(serverPlayer.server).craftedAmount(terminal, player.getUUID(), key);
            if (available > 0) return Math.min(amount, available);
        }
        notice(player, decision);
        return 0;
    }

    public static void extracted(IGrid grid, IActionSource source, AEKey key, long amount, Actionable mode) {
        if (mode != Actionable.MODULATE || amount <= 0) return;
        var terminal = craftingTerminal(grid);
        if (terminal != null && source.player().orElse(null) instanceof ServerPlayer player) {
            // Also spend allowances when Extract is enabled, so revoking it cannot permit a second collection.
            // Ingredients pulled by the player's own later job spend them too: that product is no longer collectible.
            SecuritySavedData.get(player.server).consumeCraftedOutput(terminal, player.getUUID(), key, amount);
        }
    }

    public static boolean opening(Player player, Object host, ResourceLocation type) {
        if (player.level().isClientSide()) return true;
        if (!liveHost(player, host)) return false;
        String name = type.getPath();
        boolean ae2 = type.getNamespace().equals("ae2");
        boolean storage = ae2 && STORAGE_MENUS.contains(name)
                || type.getNamespace().equals("ae2wtlib") && WIRELESS_VIEW_MENUS.contains(name);
        boolean craftingRequest = ae2 && Set.of("craftamount", "craftconfirm").contains(name);
        boolean status = ae2 && Set.of("craftingcpu", "craftingstatus").contains(name);
        if (ae2 && HARDWARE_MENUS.contains(name)) {
            if (host instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
                return SecurityHardware.allowBuild(blockEntity, player);
            }
            if (host instanceof appeng.api.parts.IPart part) {
                return SecurityHardware.allowBuild(part, player);
            }
            return allow(grid(host), player, Permission.BUILD, false);
        }
        // An uninitialized native host is unresolved, never a public network.
        if (host instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity
                && SecurityHardware.nativeHost(blockEntity) && SecurityHardware.networks(blockEntity).unresolved) {
            notice(player, AccessDecision.UNRESOLVED);
            return false;
        }
        return allow(grid(host), player, craftingRequest ? Permission.CRAFT : Permission.VIEW,
                !storage && !craftingRequest && !status);
    }

    private static boolean liveHost(Player player, Object host) {
        net.minecraft.world.level.block.entity.BlockEntity blockEntity;
        if (host instanceof appeng.parts.AEBasePart part) {
            var partHost = part.getHost();
            if (partHost == null || !partHost.isInWorld() || partHost.getPart(part.getSide()) != part) return false;
            blockEntity = partHost.getBlockEntity();
        } else if (host instanceof net.minecraft.world.level.block.entity.BlockEntity block) {
            blockEntity = block;
        } else {
            return true; // Item and wireless hosts retain AE2's own validation and range rules.
        }
        var pos = blockEntity.getBlockPos();
        return !blockEntity.isRemoved() && blockEntity.getLevel() == player.level()
                && SecurityHardware.loaded(player.level(), pos) && player.level().getBlockEntity(pos) == blockEntity
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    public static boolean menu(AEBaseMenu menu) {
        if (menu.isClientSide()) return true;
        var type = BuiltInRegistries.MENU.getKey(menu.getType());
        boolean allowed = opening(menu.getPlayer(), menu.getTarget(), type);
        if (!allowed) menu.setValidMenu(false);
        return allowed;
    }

    /**
     * AE2's setting packets act on the open menu's target. A crafting CPU opens with View so its status can
     * be read, but its scheduling mode is hardware configuration and needs Build. Terminal and ME Chest
     * targets only carry display preferences (sort order, view mode) and stay available to every viewer.
     */
    public static boolean configuring(AEBaseMenu menu) {
        if (menu.isClientSide() || !(menu.getTarget() instanceof CraftingBlockEntity cpu)) return true;
        return SecurityHardware.allowBuild(cpu, menu.getPlayer());
    }

    public static boolean craftMenu(AEBaseMenu menu) {
        if (menu.isClientSide()) return true;
        return menu(menu) && allow(grid(menu.getTarget()), menu.getPlayer(), Permission.CRAFT, false);
    }

    public static void policyChanged(MinecraftServer server, UUID terminal, SecurityPolicy before, SecurityPolicy after) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof AEBaseMenu menu) {
                var grid = grid(menu.getTarget());
                if (grid != null && grid.getService(ISecurityGrid.class).terminalIds().contains(terminal)) {
                    var type = BuiltInRegistries.MENU.getKey(menu.getType());
                    boolean lostPermission = (before.permissions(player.getUUID()) & ~after.permissions(player.getUUID())) != 0;
                    if (lostPermission || !opening(player, menu.getTarget(), type)) player.closeContainer();
                }
            }
        }
    }

    public static void notice(Player player, AccessDecision decision) {
        notice(player, Component.translatable(decision.translationKey()));
    }

    public static void notice(Player player, Component message) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        long tick = serverPlayer.server.getTickCount();
        Long previous = LAST_NOTICE.get(player);
        if (previous == null || tick - previous >= 20 || tick < previous) {
            player.displayClientMessage(message, true);
            LAST_NOTICE.put(player, tick);
        }
    }
}
