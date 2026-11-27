package dev.ae2security.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import appeng.api.networking.IGrid;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.localization.GuiText;
import appeng.items.tools.powered.WirelessTerminalItem;
import dev.ae2security.MESecurity;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The Intrusion Alert Card. A player hears about a stranger being denied on a network when they are online,
 * have standing there (owner or trusted), and carry a wireless terminal that holds the card and is linked to
 * one of that network's wireless access points. Range and dimension do not matter, and the network's chunks
 * need not be loaded: the terminal only has to remember an access point that is on the network.
 */
public final class IntrusionAlerts {
    private IntrusionAlerts() {}

    /**
     * Lets the card into every wireless terminal, including those of add-ons such as AE2 Wireless Terminals,
     * one card per terminal. Call during common setup, once all items are registered.
     */
    public static void registerCard() {
        var card = MESecurity.ALERT_CARD.get();
        var group = GuiText.WirelessTerminals.getTranslationKey();
        for (var item : BuiltInRegistries.ITEM) {
            if (item instanceof WirelessTerminalItem) Upgrades.add(card, item, 1, group);
        }
    }

    /**
     * Who is told about a stranger denied on this grid: everyone online with standing on the network who
     * carries a carded terminal linked to it. Never the intruder.
     */
    public static List<ServerPlayer> recipients(Collection<? extends ServerPlayer> online, IGrid grid,
            SecurityPolicy policy, UUID intruder) {
        if (grid == null || online.isEmpty()) return List.of();
        Set<GlobalPos> accessPoints = null;
        var recipients = new ArrayList<ServerPlayer>();
        for (var player : online) {
            if (player.getUUID().equals(intruder) || !SecuritySavedData.hasStanding(policy, player.getUUID())) continue;
            if (accessPoints == null) {
                accessPoints = accessPoints(grid);
                if (accessPoints.isEmpty()) return List.of();
            }
            if (carriesCard(player, accessPoints)) recipients.add(player);
        }
        return recipients;
    }

    /** Shows the alert above the hotbar with a ping that only the recipient hears. */
    static void send(ServerPlayer recipient, Component alert) {
        recipient.displayClientMessage(alert, true);
        recipient.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.6F, 1.8F);
    }

    /** Whether the player's inventory or offhand holds a carded terminal linked to one of these access points. */
    static boolean carriesCard(Player player, Set<GlobalPos> accessPoints) {
        var card = MESecurity.ALERT_CARD.get();
        var inventory = player.getInventory();
        for (var stacks : List.of(inventory.items, inventory.offhand)) {
            for (var stack : stacks) {
                if (stack.getItem() instanceof WirelessTerminalItem terminal
                        && accessPoints.contains(terminal.getLinkedPosition(stack))
                        && terminal.getUpgrades(stack).isInstalled(card)) return true;
            }
        }
        return false;
    }

    /** Where this grid's access points are, which is what a linked wireless terminal remembers. */
    private static Set<GlobalPos> accessPoints(IGrid grid) {
        var accessPoints = new HashSet<GlobalPos>();
        for (var accessPoint : grid.getMachines(WirelessAccessPointBlockEntity.class)) {
            var level = accessPoint.getLevel();
            if (level != null) accessPoints.add(GlobalPos.of(level.dimension(), accessPoint.getBlockPos()));
        }
        return accessPoints;
    }
}
