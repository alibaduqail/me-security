package dev.ae2security.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import appeng.api.networking.IGrid;
import com.mojang.logging.LogUtils;
import dev.ae2security.part.SecurityTerminalPart;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The intrusion alarm. When a player with no standing on a protected network (neither its owner nor
 * trusted) is denied, the network's Security Terminal tells its owner and, if the owner switched that
 * on, its block emits a redstone pulse. A trusted player who lacks one permission never raises it.
 *
 * <p>It reacts to denials and decides nothing: no access check reads anything kept here.
 */
public final class SecurityAlarm {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How long the terminal's block emits redstone after a denied attempt. A further attempt restarts it. */
    public static final int PULSE_TICKS = 100;
    /** One report per intruder and terminal in this many ticks, however often they try. */
    static final int REPORT_INTERVAL_TICKS = 1200;
    private static final int MAX_REPORT_KEYS = 1024;
    /** Intruders named in a login summary before the rest are counted. */
    private static final int DIGEST_NAMES = 5;

    private record ReportKey(UUID terminal, UUID intruder) {}
    /** Server tick of the last report. Only touched on the server thread; cleared when the server stops. */
    private static final Map<ReportKey, Long> LAST_REPORT = new HashMap<>();
    /** Terminals emitting redstone and the tick it ends. Read by redstone queries from any thread. */
    private static final Map<SecurityTerminalPart, Long> PULSING = new ConcurrentHashMap<>();
    private static final ThreadLocal<int[]> QUIET = ThreadLocal.withInitial(() -> new int[1]);

    private SecurityAlarm() {}

    public static void register(IEventBus bus) {
        bus.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                digest(player.server, player.getUUID()).forEach(player::sendSystemMessage);
            }
        });
        bus.addListener((ServerStoppedEvent event) -> {
            LAST_REPORT.clear();
            PULSING.clear();
        });
    }

    /**
     * Runs a check that re-validates access a player already had, or that the player did not start, such
     * as an open menu being checked every tick. A denial there is not an attempt to get in.
     */
    public static boolean quietly(BooleanSupplier check) {
        var depth = QUIET.get();
        depth[0]++;
        try {
            return check.getAsBoolean();
        } finally {
            depth[0]--;
        }
    }

    /** A player was denied on this grid. Nothing is raised while terminals conflict or none is connected. */
    public static void denied(IGrid grid, Player player, Permission permission) {
        if (grid == null || !(player instanceof ServerPlayer intruder) || QUIET.get()[0] > 0) return;
        var security = grid.getService(ISecurityGrid.class);
        var ids = security.terminalIds();
        if (ids.size() != 1) return;
        var id = ids.iterator().next();
        raise(intruder, id, security instanceof SecurityGridService service ? service.terminal(id) : null, permission);
    }

    /** A player was denied at the Security Terminal itself. */
    public static void denied(SecurityTerminalPart terminal, Player player, Permission permission) {
        if (!(player instanceof ServerPlayer intruder) || QUIET.get()[0] > 0) return;
        raise(intruder, terminal.terminalId(), terminal, permission);
    }

    /** @param terminal the loaded part carrying the identity, or null when its chunk is not loaded */
    private static void raise(ServerPlayer intruder, UUID terminalId, SecurityTerminalPart terminal, Permission permission) {
        var server = intruder.server;
        if (!server.isSameThread()) {
            server.execute(() -> raise(intruder, terminalId, terminal, permission));
            return;
        }
        var data = SecuritySavedData.get(server);
        var policy = data.policy(terminalId);
        if (policy == null || SecuritySavedData.hasStanding(policy, intruder.getUUID())
                || SecurityAccess.bypassing(intruder)) return;
        var mode = data.alarmMode(terminalId);
        if (!mode.alerts()) return;
        long tick = server.getTickCount();
        if (mode.redstone() && terminal != null) pulse(terminal, tick);
        if (!due(terminalId, intruder.getUUID(), tick)) return;

        var name = intruder.getGameProfile().getName();
        var where = data.location(terminalId);
        LOGGER.info("ME Security alarm: {} ({}) was denied {} on the network of Security Terminal {} at {}",
                name, intruder.getUUID(), permission.name().toLowerCase(Locale.ROOT), terminalId,
                where == null ? "an unknown location" : where);
        var owner = server.getPlayerList().getPlayer(policy.owner());
        if (owner != null) owner.sendSystemMessage(liveAlert(name, permission, where));
        else data.recordAlarm(terminalId, intruder.getUUID(), name, System.currentTimeMillis());
    }

    /** Whether this intruder's attempt is reported, and if so starts their next interval. */
    static boolean due(UUID terminal, UUID intruder, long tick) {
        var key = new ReportKey(terminal, intruder);
        var last = LAST_REPORT.get(key);
        if (last != null && tick >= last && tick - last < REPORT_INTERVAL_TICKS) return false;
        if (last == null && LAST_REPORT.size() >= MAX_REPORT_KEYS) {
            LAST_REPORT.values().removeIf(at -> tick < at || tick - at >= REPORT_INTERVAL_TICKS);
            if (LAST_REPORT.size() >= MAX_REPORT_KEYS) LAST_REPORT.clear();
        }
        LAST_REPORT.put(key, tick);
        return true;
    }

    static void forgetReports() { LAST_REPORT.clear(); }

    // ---- redstone ----

    /** True while the terminal's block emits its alarm signal. Cheap enough for every redstone query. */
    public static boolean pulsing(SecurityTerminalPart terminal) {
        return !PULSING.isEmpty() && PULSING.containsKey(terminal);
    }

    public static boolean anyPulsing() { return !PULSING.isEmpty(); }

    /** The terminal is leaving the world; whatever replaces its block updates the neighbors. */
    public static void forget(SecurityTerminalPart terminal) {
        if (!PULSING.isEmpty()) PULSING.remove(terminal);
    }

    private static void pulse(SecurityTerminalPart terminal, long tick) {
        if (PULSING.put(terminal, tick + PULSE_TICKS) == null) notifyNeighbors(terminal);
    }

    static void tick(MinecraftServer server) {
        if (PULSING.isEmpty()) return;
        long now = server.getTickCount();
        List<SecurityTerminalPart> ended = null;
        for (var pulse : PULSING.entrySet()) {
            // A tick count below the start means a new server run; the pulse is over either way.
            if (now < pulse.getValue() && now >= pulse.getValue() - PULSE_TICKS) continue;
            if (ended == null) ended = new ArrayList<>();
            ended.add(pulse.getKey());
        }
        if (ended == null) return;
        // The signal must already read as off when the neighbors look.
        for (var terminal : ended) PULSING.remove(terminal);
        for (var terminal : ended) notifyNeighbors(terminal);
    }

    private static void notifyNeighbors(SecurityTerminalPart terminal) {
        var host = terminal.getHost();
        if (host != null && host.isInWorld()) host.notifyNeighbors();
    }

    // ---- messages ----

    /** What the owner is told at login. Takes everything that was waiting for them. */
    public static List<Component> digest(MinecraftServer server, UUID owner) {
        var data = SecuritySavedData.get(server);
        var waiting = data.takeAlarms(owner);
        if (waiting.isEmpty()) return List.of();
        long now = System.currentTimeMillis();
        var lines = new ArrayList<Component>(waiting.size());
        waiting.forEach((terminal, entries) -> lines.add(digestLine(entries, data.location(terminal), now)));
        return lines;
    }

    private static Component liveAlert(String intruder, Permission permission, TerminalLocation where) {
        return Component.translatable("message.ae2security.alarm.live", intruder,
                Component.translatable("permission.ae2security." + permission.name().toLowerCase(Locale.ROOT)),
                location(where)).withStyle(ChatFormatting.YELLOW);
    }

    /** @param entries most recent first, never empty */
    private static Component digestLine(List<AlarmLog.Entry> entries, TerminalLocation where, long nowMillis) {
        var names = new ArrayList<Component>();
        for (var entry : entries.subList(0, Math.min(DIGEST_NAMES, entries.size()))) {
            names.add(entry.count() > 1
                    ? Component.translatable("message.ae2security.alarm.repeated", entry.name(), entry.count())
                    : Component.literal(entry.name()));
        }
        if (entries.size() > DIGEST_NAMES) {
            names.add(Component.translatable("message.ae2security.alarm.more", entries.size() - DIGEST_NAMES));
        }
        var ago = ago(nowMillis - entries.get(0).lastMillis());
        return Component.translatable("message.ae2security.alarm.digest",
                ComponentUtils.formatList(names, Component.literal(", ")),
                Component.translatable("message.ae2security.alarm.ago." + ago.unit(), ago.amount()),
                location(where)).withStyle(ChatFormatting.YELLOW);
    }

    private static Component location(TerminalLocation where) {
        if (where == null) return Component.translatable("message.ae2security.alarm.location_unknown");
        return Component.translatable("message.ae2security.alarm.location", where.pos().getX(), where.pos().getY(),
                where.pos().getZ(), where.dimension());
    }

    /** A coarse age: {@code unit} is the suffix of the translation key and {@code amount} its argument. */
    record Ago(String unit, long amount) {}

    static Ago ago(long elapsedMillis) {
        long minutes = Math.max(0, elapsedMillis) / 60_000;
        if (minutes < 1) return new Ago("moments", 0);
        if (minutes < 60) return new Ago("minutes", minutes);
        if (minutes < 48 * 60) return new Ago("hours", minutes / 60);
        return new Ago("days", minutes / (24 * 60));
    }
}
