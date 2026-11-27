package dev.ae2security.network;

import dev.ae2security.menu.SecurityMenu;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SecurityPayloads {
    private SecurityPayloads() {}

    /**
     * The alarm payloads are versioned apart from the policy protocol and optional, so a client and a
     * server of which only one knows them still connect: the alarm button is then simply absent.
     */
    public static final String ALARM_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("6");
        registrar.playToServer(EditPolicy.TYPE, EditPolicy.CODEC, (edit, context) -> context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof SecurityMenu menu && menu.containerId == edit.menuId()) {
                menu.edit(player, edit);
            }
        }));
        registrar.playToClient(PolicyEditResult.TYPE, PolicyEditResult.CODEC, (result, context) ->
                context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof SecurityMenu menu
                            && menu.containerId == result.menuId()) menu.acceptEditResult(result);
                }));
        registrar.playToServer(PlayerSearchRequest.TYPE, PlayerSearchRequest.CODEC, (request, context) ->
                context.enqueueWork(() -> {
                    var player = context.player();
                    if (player.containerMenu instanceof SecurityMenu menu
                            && menu.containerId == request.menuId()) menu.search(player, request);
                }));
        registrar.playToClient(PlayerSearchResult.TYPE, PlayerSearchResult.CODEC, (result, context) ->
                context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof SecurityMenu menu
                            && menu.containerId == result.menuId()) menu.acceptSearchResult(result);
                }));
        registrar.playToClient(PolicySnapshot.TYPE, PolicySnapshot.CODEC, (snapshot, context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof SecurityMenu menu && menu.containerId == snapshot.menuId()) {
                menu.acceptSnapshot(snapshot);
            }
        }));

        var alarm = event.registrar(ALARM_VERSION).optional();
        alarm.playToServer(SetAlarm.TYPE, SetAlarm.CODEC, (request, context) -> context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof SecurityMenu menu && menu.containerId == request.menuId()) {
                menu.setAlarm(player, request);
            }
        }));
        alarm.playToClient(AlarmState.TYPE, AlarmState.CODEC, (state, context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof SecurityMenu menu && menu.containerId == state.menuId()) {
                menu.acceptAlarmState(state);
            }
        }));
    }
}
