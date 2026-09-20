package dev.ae2security.network;

import dev.ae2security.menu.SecurityMenu;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SecurityPayloads {
    private SecurityPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");
        registrar.playToServer(EditPolicy.TYPE, EditPolicy.CODEC, (edit, context) -> context.enqueueWork(() -> {
            var player = context.player();
            if (player.containerMenu instanceof SecurityMenu menu && menu.containerId == edit.menuId()) {
                menu.edit(player, edit);
            }
        }));
        registrar.playToClient(PolicySnapshot.TYPE, PolicySnapshot.CODEC, (snapshot, context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof SecurityMenu menu && menu.containerId == snapshot.menuId()) {
                menu.acceptSnapshot(snapshot);
            }
        }));
    }
}
