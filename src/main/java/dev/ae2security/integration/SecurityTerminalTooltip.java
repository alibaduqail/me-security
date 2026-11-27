package dev.ae2security.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import appeng.api.integrations.igtooltip.PartTooltips;
import appeng.api.integrations.igtooltip.TooltipBuilder;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.api.networking.IGrid;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.AccessDecision;
import dev.ae2security.security.ISecurityGrid;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityAccess;
import dev.ae2security.security.SecurityPolicy;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Jade and WTHIT lines for the Security Terminal. They go through AE2's own in-game tooltip API, which
 * AE2 forwards to whichever of those mods is installed, so neither is a dependency of ME Security.
 * The owner's name is shown to everyone; the access line describes only the player looking at it.
 */
public final class SecurityTerminalTooltip {
    static final String TAG = "ae2security";
    private static final String STATUS = "status";
    private static final String OWNER = "owner";
    private static final String ACCESS = "access";

    /** What the looking player is shown. The order of names is part of the client-server data. */
    public enum Status { OWNER, TRUSTED, NONE, BYPASS, CONFLICT, UNRESOLVED, RETIRED }

    public record Shown(Status status, String owner, int access) {
        public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString(STATUS, status.name());
            if (owner != null) tag.putString(OWNER, owner);
            tag.putInt(ACCESS, access);
            return tag;
        }

        /** Returns null for missing or unknown data, so a mismatched server shows nothing instead of a wrong line. */
        public static Shown fromTag(CompoundTag tag) {
            if (!tag.contains(STATUS)) return null;
            Status status;
            try {
                status = Status.valueOf(tag.getString(STATUS).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return null;
            }
            return new Shown(status, tag.contains(OWNER) ? tag.getString(OWNER) : null, tag.getInt(ACCESS));
        }
    }

    private SecurityTerminalTooltip() {}

    /** AE2 keeps these in a static registry that it reads lazily, so this may run during mod construction. */
    public static void register() {
        PartTooltips.addServerData(SecurityTerminalPart.class, SecurityTerminalTooltip::provideServerData);
        PartTooltips.addBody(SecurityTerminalPart.class, SecurityTerminalTooltip::buildTooltip);
    }

    public static void provideServerData(Player player, SecurityTerminalPart terminal, CompoundTag data) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var saved = SecuritySavedData.get(serverPlayer.server);
        var policy = saved.policy(terminal.terminalId());
        var grid = grid(terminal);
        var decision = grid == null ? null
                : grid.getService(ISecurityGrid.class).check(player.getUUID(), Permission.VIEW, false);
        var shown = evaluate(policy, saved.retired(terminal.terminalId()), decision, player.getUUID(),
                SecurityAccess.bypassing(player), policy == null ? null : saved.name(policy.owner()));
        data.put(TAG, shown.toTag());
    }

    /**
     * The network decision comes first: a conflict or a missing policy blocks everyone, the owner included.
     * Otherwise the network has exactly this terminal's policy, so its permissions are the player's access.
     */
    public static Shown evaluate(SecurityPolicy policy, boolean retired, AccessDecision networkDecision,
            UUID player, boolean bypassing, String ownerName) {
        if (policy == null) return new Shown(retired ? Status.RETIRED : Status.UNRESOLVED, null, 0);
        if (bypassing) return new Shown(Status.BYPASS, ownerName, Permission.ALL);
        if (networkDecision == AccessDecision.CONFLICT) return new Shown(Status.CONFLICT, ownerName, 0);
        if (networkDecision == AccessDecision.UNRESOLVED) return new Shown(Status.UNRESOLVED, ownerName, 0);
        if (policy.isOwner(player)) return new Shown(Status.OWNER, ownerName, Permission.ALL);
        int access = policy.permissions(player);
        return new Shown(access == 0 ? Status.NONE : Status.TRUSTED, ownerName, access);
    }

    private static void buildTooltip(SecurityTerminalPart terminal, TooltipContext context, TooltipBuilder tooltip) {
        var tag = context.serverData().getCompound(TAG);
        var shown = Shown.fromTag(tag);
        if (shown != null) lines(shown).forEach(tooltip::addLine);
    }

    public static List<Component> lines(Shown shown) {
        var lines = new ArrayList<Component>(2);
        if (shown.owner() != null) lines.add(Component.translatable("tooltip.ae2security.owner", shown.owner()));
        lines.add(switch (shown.status()) {
            case OWNER -> access(Component.translatable("tooltip.ae2security.access.owner"), ChatFormatting.GREEN);
            case TRUSTED -> access(permissions(shown.access()), ChatFormatting.GREEN);
            case NONE -> access(Component.translatable("tooltip.ae2security.access.none"), ChatFormatting.RED);
            case BYPASS -> access(Component.translatable("tooltip.ae2security.access.bypass"), ChatFormatting.GOLD);
            case CONFLICT -> Component.translatable("tooltip.ae2security.conflict").withStyle(ChatFormatting.RED);
            case UNRESOLVED -> Component.translatable("tooltip.ae2security.unresolved").withStyle(ChatFormatting.RED);
            case RETIRED -> Component.translatable("tooltip.ae2security.retired").withStyle(ChatFormatting.GRAY);
        });
        return lines;
    }

    private static Component access(Component value, ChatFormatting color) {
        return Component.translatable("tooltip.ae2security.access", value.copy().withStyle(color));
    }

    /** View first, then the order of the owner screen's switches. */
    private static Component permissions(int mask) {
        var names = new ArrayList<Component>();
        if (Permission.VIEW.in(mask)) names.add(name(Permission.VIEW));
        for (var permission : Permission.editable()) {
            if (permission.in(mask)) names.add(name(permission));
        }
        return ComponentUtils.formatList(names, Component.literal(", "));
    }

    private static Component name(Permission permission) {
        return Component.translatable("permission.ae2security." + permission.name().toLowerCase(Locale.ROOT));
    }

    /** A node that is being torn down has no grid; AE2 reports that by throwing. */
    private static IGrid grid(SecurityTerminalPart terminal) {
        var node = terminal.getGridNode();
        if (node == null) return null;
        try {
            return node.getGrid();
        } catch (IllegalStateException destroyed) {
            return null;
        }
    }
}
