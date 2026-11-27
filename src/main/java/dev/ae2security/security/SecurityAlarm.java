package dev.ae2security.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import appeng.api.networking.IGrid;
import com.mojang.logging.LogUtils;
import dev.ae2security.part.SecurityEmitterPart;
import dev.ae2security.part.SecurityTerminalPart;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The intrusion alarm. When a player is denied on a protected network, every Security Emitter on it
 * that watches for that kind of player emits a short redstone pulse, as long as an observer's. When the
 * player has no standing there (neither owner nor trusted), everyone with standing who carries an
 * Intrusion Alert Card for the network is told; see {@link IntrusionAlerts}.
 *
 * <p>It reacts to denials and decides nothing: no access check reads anything kept here.
 */
public final class SecurityAlarm {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * How long a Security Emitter stays triggered after a denied attempt: two game ticks, an observer's
     * pulse. An attempt while the pulse is on adds nothing, so every pulse is followed by a gap.
     */
    public static final int PULSE_TICKS = 2;
    /** One report per intruder and terminal in this many ticks, however often they try. */
    static final int REPORT_INTERVAL_TICKS = 1200;
    private static final int MAX_REPORT_KEYS = 1024;

    private record ReportKey(UUID terminal, UUID intruder) {}
    /** Server tick of the last report. Only touched on the server thread; cleared when the server stops. */
    private static final Map<ReportKey, Long> LAST_REPORT = new HashMap<>();
    /** Triggered emitters and the tick their pulse ends. */
    private static final Map<SecurityEmitterPart, Long> PULSING = new ConcurrentHashMap<>();
    /** Emitters whose output changes at the end of this server tick, never in the middle of the denied action. */
    private static final Set<SecurityEmitterPart> CHANGED = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<int[]> QUIET = ThreadLocal.withInitial(() -> new int[1]);

    private SecurityAlarm() {}

    public static void register(IEventBus bus) {
        bus.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        bus.addListener((ServerStoppedEvent event) -> {
            LAST_REPORT.clear();
            PULSING.clear();
            CHANGED.clear();
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
        if (grid == null || !(player instanceof ServerPlayer actor) || QUIET.get()[0] > 0) return;
        var ids = grid.getService(ISecurityGrid.class).terminalIds();
        if (ids.size() != 1) return;
        raise(actor, grid, ids.iterator().next(), permission);
    }

    /** A player was denied at the Security Terminal itself. */
    public static void denied(SecurityTerminalPart terminal, Player player, Permission permission) {
        if (!(player instanceof ServerPlayer actor) || QUIET.get()[0] > 0) return;
        var node = terminal.getGridNode();
        raise(actor, node == null ? null : node.getGrid(), terminal.terminalId(), permission);
    }

    /** @param grid the network whose emitters react, or null when the terminal is not on one */
    private static void raise(ServerPlayer actor, IGrid grid, UUID terminalId, Permission permission) {
        var server = actor.server;
        if (!server.isSameThread()) {
            server.execute(() -> raise(actor, grid, terminalId, permission));
            return;
        }
        var data = SecuritySavedData.get(server);
        var policy = data.policy(terminalId);
        if (policy == null || SecurityAccess.bypassing(actor)) return;
        boolean stranger = !SecuritySavedData.hasStanding(policy, actor.getUUID());
        long tick = server.getTickCount();
        if (grid != null) {
            for (var emitter : grid.getMachines(SecurityEmitterPart.class)) {
                if (emitter.trigger().reactsTo(stranger)) pulse(emitter, tick);
            }
        }
        if (!stranger || !due(terminalId, actor.getUUID(), tick)) return;

        var name = actor.getGameProfile().getName();
        var where = data.location(terminalId);
        LOGGER.info("ME Security alarm: {} ({}) was denied {} on the network of Security Terminal {} at {}",
                name, actor.getUUID(), permission.name().toLowerCase(Locale.ROOT), terminalId,
                where == null ? "an unknown location" : where);
        var recipients = IntrusionAlerts.recipients(server.getPlayerList().getPlayers(), grid, policy, actor.getUUID());
        if (recipients.isEmpty()) return;
        var alert = alert(name, permission, where);
        for (var recipient : recipients) IntrusionAlerts.send(recipient, alert);
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

    // ---- Security Emitters ----

    /** True while the emitter is triggered, before its own redstone mode is applied. */
    public static boolean pulsing(SecurityEmitterPart emitter) {
        return !PULSING.isEmpty() && PULSING.containsKey(emitter);
    }

    /** The emitter is leaving the world; whatever replaces it updates the neighbors. */
    public static void forget(SecurityEmitterPart emitter) {
        if (!PULSING.isEmpty()) PULSING.remove(emitter);
        CHANGED.remove(emitter);
    }

    private static void pulse(SecurityEmitterPart emitter, long tick) {
        if (PULSING.putIfAbsent(emitter, tick + PULSE_TICKS) == null) CHANGED.add(emitter);
    }

    static void tick(MinecraftServer server) {
        if (!PULSING.isEmpty()) {
            long now = server.getTickCount();
            for (var pulse = PULSING.entrySet().iterator(); pulse.hasNext();) {
                var entry = pulse.next();
                // A tick count below the start means a new server run; the pulse is over either way.
                if (now < entry.getValue() && now >= entry.getValue() - PULSE_TICKS) continue;
                pulse.remove();
                CHANGED.add(entry.getKey());
            }
        }
        if (CHANGED.isEmpty()) return;
        var changed = new ArrayList<>(CHANGED);
        CHANGED.clear();
        for (var emitter : changed) emitter.alarmChanged();
    }

    // ---- messages ----

    private static Component alert(String intruder, Permission permission, TerminalLocation where) {
        return Component.translatable("message.ae2security.alarm.live", intruder,
                Component.translatable("permission.ae2security." + permission.name().toLowerCase(Locale.ROOT)),
                location(where)).withStyle(ChatFormatting.YELLOW);
    }

    private static Component location(TerminalLocation where) {
        if (where == null) return Component.translatable("message.ae2security.alarm.location_unknown");
        return Component.translatable("message.ae2security.alarm.location", where.pos().getX(), where.pos().getY(),
                where.pos().getZ(), where.dimension());
    }
}
