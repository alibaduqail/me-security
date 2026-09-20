package dev.ae2security.security;

import java.util.*;
import appeng.api.networking.*;
import appeng.api.networking.security.*;
import appeng.api.storage.IPatternAccessTermMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
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
    private SecurityAccess() {}

    public static IGrid grid(Object host) {
        IGridNode node = null;
        if (host instanceof IActionHost actionHost) node = actionHost.getActionableNode();
        if (node == null && host instanceof IPatternAccessTermMenuHost patternHost) node = patternHost.getGridNode();
        if (node != null) return node.getGrid();
        if (host instanceof WirelessTerminalMenuHost<?> wireless && !wireless.isClientSide()) {
            return wireless.getItem().getLinkedGrid(wireless.getItemStack(), wireless.getPlayer().level(), null);
        }
        return null;
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
        if (player == null) return true; // AE2 machine automation has no player principal.
        if (source instanceof CraftingActionSource crafting && crafting.grid() == grid) {
            return allow(grid, player, Permission.CRAFT, false);
        }
        return allow(grid, player, permission, false);
    }

    public static boolean opening(Player player, Object host, ResourceLocation type) {
        if (player.level().isClientSide()) return true;
        String name = type.getPath();
        boolean ae2 = type.getNamespace().equals("ae2");
        boolean storage = ae2 && STORAGE_MENUS.contains(name)
                || type.getNamespace().equals("ae2wtlib") && name.equals("wireless_crafting_terminal");
        boolean craftingRequest = ae2 && Set.of("craftamount", "craftconfirm").contains(name);
        boolean status = ae2 && Set.of("craftingcpu", "craftingstatus").contains(name);
        return allow(grid(host), player, craftingRequest ? Permission.CRAFT : Permission.VIEW,
                !storage && !craftingRequest && !status);
    }

    public static boolean menu(AEBaseMenu menu) {
        if (menu.isClientSide()) return true;
        var type = BuiltInRegistries.MENU.getKey(menu.getType());
        boolean allowed = opening(menu.getPlayer(), menu.getTarget(), type);
        if (!allowed) menu.setValidMenu(false);
        return allowed;
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
