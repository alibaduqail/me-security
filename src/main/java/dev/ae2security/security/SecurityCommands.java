package dev.ae2security.security;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * /mesecurity: operator recovery for networks whose owner is gone. Requires permission level 2.
 * The handlers are public so that tests can call them without going through command parsing.
 */
public final class SecurityCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PERMISSION_LEVEL = SecurityAccess.BYPASS_PERMISSION_LEVEL;
    private static final int MAX_SUGGESTIONS = 100;

    private SecurityCommands() {}

    public static void register(IEventBus bus) {
        bus.addListener(SecurityCommands::onRegisterCommands);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> SecurityAccess.clearBypass(event.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent event) -> SecurityAccess.clearBypass());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var terminal = Commands.argument("terminal", UuidArgument.uuid());
        dispatcher.register(Commands.literal("mesecurity")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource()))
                        .then(terminal.executes(context -> info(context.getSource(),
                                UuidArgument.getUuid(context, "terminal")))))
                .then(Commands.literal("setowner")
                        .then(Commands.argument("terminal", UuidArgument.uuid())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                playerNames(context.getSource().getServer(), builder.getRemaining()), builder))
                                        .executes(context -> setOwner(context.getSource(),
                                                UuidArgument.getUuid(context, "terminal"),
                                                StringArgumentType.getString(context, "player"))))))
                .then(Commands.literal("retire")
                        .then(Commands.argument("terminal", UuidArgument.uuid())
                                .executes(context -> retire(context.getSource(),
                                        UuidArgument.getUuid(context, "terminal")))))
                .then(Commands.literal("bypass").executes(context -> bypass(context.getSource()))));
    }

    // ---- info ----

    /** Describes the terminal governing the block the executing player is looking at. */
    public static int info(CommandSourceStack source) {
        var player = playerOrFail(source);
        if (player == null) return 0;
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return fail(source, "commands.ae2security.info.no_block");
        }
        var host = player.level().getBlockEntity(block.getBlockPos());
        if (!SecurityHardware.gridHost(host)) return fail(source, "commands.ae2security.info.not_network");

        var networks = SecurityHardware.networks(host);
        var ids = new TreeSet<UUID>();
        for (var grid : networks.grids) ids.addAll(grid.getService(ISecurityGrid.class).terminalIds());
        if (ids.isEmpty()) {
            reply(source, Component.translatable(networks.unresolved ? "commands.ae2security.info.unresolved_network"
                    : "commands.ae2security.info.public"));
            return 0;
        }
        var data = SecuritySavedData.get(source.getServer());
        if (ids.size() > 1) reply(source, Component.translatable("commands.ae2security.info.conflict", ids.size()));
        for (var id : ids) describe(source, data, id);
        return ids.size();
    }

    /** Describes one terminal by identity; works from the console. */
    public static int info(CommandSourceStack source, UUID id) {
        describe(source, SecuritySavedData.get(source.getServer()), id);
        return 1;
    }

    private static void describe(CommandSourceStack source, SecuritySavedData data, UUID id) {
        reply(source, Component.translatable("commands.ae2security.info.terminal", copyable(id)));
        var policy = data.policy(id);
        if (policy == null) {
            reply(source, Component.translatable(data.retired(id) ? "commands.ae2security.info.retired"
                    : "commands.ae2security.info.no_policy", id.toString()));
            return;
        }
        reply(source, Component.translatable("commands.ae2security.info.owner", data.name(policy.owner()),
                copyable(policy.owner())));
        if (policy.trusted().isEmpty()) {
            reply(source, Component.translatable("commands.ae2security.info.no_trusted"));
            return;
        }
        reply(source, Component.translatable("commands.ae2security.info.trusted", policy.trusted().size()));
        policy.trusted().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByKey(
                        (a, b) -> data.name(a).compareToIgnoreCase(data.name(b))))
                .forEach(entry -> reply(source, Component.translatable("commands.ae2security.info.trusted_entry",
                        data.name(entry.getKey()), permissionNames(entry.getValue()))));
    }

    private static String permissionNames(int mask) {
        var names = new ArrayList<String>();
        for (var permission : Permission.values()) {
            if (permission.in(mask)) names.add(permission.name().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", names);
    }

    private static MutableComponent copyable(UUID id) {
        return Component.literal(id.toString()).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id.toString()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("commands.ae2security.click_to_copy"))));
    }

    // ---- setowner ----

    public static int setOwner(CommandSourceStack source, UUID id, String playerName) {
        var server = source.getServer();
        var data = SecuritySavedData.get(server);
        var policy = data.policy(id);
        if (policy == null) {
            return fail(source, data.retired(id) ? "commands.ae2security.error.retired"
                    : "commands.ae2security.error.no_policy", id.toString(), id.toString());
        }
        var matches = findPlayers(server, data, playerName);
        if (matches.isEmpty()) return fail(source, "commands.ae2security.setowner.unknown_player", playerName);
        if (matches.size() > 1) return fail(source, "commands.ae2security.setowner.ambiguous_player", playerName);
        var target = matches.get(0);
        if (policy.isOwner(target.id())) {
            return fail(source, "commands.ae2security.setowner.already_owner", target.name());
        }

        var updated = policy.transfer(policy.owner(), target.id());
        data.update(id, updated);
        data.rememberPlayer(target.id(), target.name());
        SecurityAccess.policyChanged(server, id, policy, updated);
        LOGGER.info("{} set the owner of ME Security Terminal {} to {} ({}); previous owner was {} ({})",
                source.getTextName(), id, target.name(), target.id(), data.name(policy.owner()), policy.owner());
        reply(source, Component.translatable("commands.ae2security.setowner.success", id.toString(), target.name()));
        return 1;
    }

    private record Candidate(UUID id, String name) {}

    /** Online players first (exact names), then the saved directory; names compare case-insensitively. */
    private static java.util.List<Candidate> findPlayers(MinecraftServer server, SecuritySavedData data, String name) {
        var found = new ArrayList<Candidate>();
        var online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            found.add(new Candidate(online.getUUID(), online.getGameProfile().getName()));
            return found;
        }
        for (var entry : data.players().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) found.add(new Candidate(entry.getKey(), entry.getValue()));
        }
        return found;
    }

    private static Iterable<String> playerNames(MinecraftServer server, String prefix) {
        var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        var filter = prefix.toLowerCase(Locale.ROOT);
        for (var player : server.getPlayerList().getPlayers()) names.add(player.getGameProfile().getName());
        for (var name : SecuritySavedData.get(server).players().values()) {
            if (names.size() >= MAX_SUGGESTIONS) break;
            if (name.toLowerCase(Locale.ROOT).startsWith(filter)) names.add(name);
        }
        return names;
    }

    // ---- retire ----

    public static int retire(CommandSourceStack source, UUID id) {
        var data = SecuritySavedData.get(source.getServer());
        if (data.retired(id)) return fail(source, "commands.ae2security.error.retired", id.toString());
        boolean hadPolicy = data.policy(id) != null;
        data.retire(id);
        LOGGER.info("{} retired ME Security Terminal {} ({})", source.getTextName(), id,
                hadPolicy ? "had a policy" : "had no policy");
        reply(source, Component.translatable("commands.ae2security.retire.success", id.toString()));
        return 1;
    }

    // ---- bypass ----

    public static int bypass(CommandSourceStack source) {
        var player = playerOrFail(source);
        if (player == null) return 0;
        boolean on = SecurityAccess.toggleBypass(player.getUUID());
        LOGGER.info("ME Security admin bypass {} for {} ({})", on ? "enabled" : "disabled",
                player.getGameProfile().getName(), player.getUUID());
        reply(source, Component.translatable(on ? "commands.ae2security.bypass.on" : "commands.ae2security.bypass.off"));
        return 1;
    }

    // ---- helpers ----

    private static net.minecraft.server.level.ServerPlayer playerOrFail(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("commands.ae2security.error.player_only"));
            return null;
        }
    }

    private static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static int fail(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(Component.translatable(key, args));
        return 0;
    }
}
