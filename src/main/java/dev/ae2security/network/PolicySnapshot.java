package dev.ae2security.network;

import java.util.*;
import dev.ae2security.MESecurity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PolicySnapshot(int menuId, UUID owner, String ownerName, long revision, String status,
        List<PlayerEntry> players) implements CustomPacketPayload {
    public static final int MAX_PLAYERS = 8192;
    public record PlayerEntry(UUID id, String name, boolean trusted, int permissions) {}
    public static final Type<PolicySnapshot> TYPE = new Type<>(MESecurity.id("policy_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PolicySnapshot> CODEC = StreamCodec.of(
            PolicySnapshot::write, PolicySnapshot::read);

    public PolicySnapshot { players = List.copyOf(players); }
    @Override public Type<PolicySnapshot> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buffer, PolicySnapshot snapshot) {
        buffer.writeVarInt(snapshot.menuId());
        buffer.writeUUID(snapshot.owner());
        buffer.writeUtf(snapshot.ownerName(), 64);
        buffer.writeLong(snapshot.revision());
        buffer.writeUtf(snapshot.status(), 64);
        buffer.writeVarInt(snapshot.players().size());
        for (var player : snapshot.players()) {
            buffer.writeUUID(player.id());
            buffer.writeUtf(player.name(), 64);
            buffer.writeBoolean(player.trusted());
            buffer.writeByte(player.permissions());
        }
    }

    private static PolicySnapshot read(RegistryFriendlyByteBuf buffer) {
        int menuId = buffer.readVarInt();
        UUID owner = buffer.readUUID();
        String name = buffer.readUtf(64);
        long revision = buffer.readLong();
        String status = buffer.readUtf(64);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PLAYERS) throw new IllegalArgumentException("Invalid player count");
        var players = new ArrayList<PlayerEntry>(count);
        for (int i = 0; i < count; i++) {
            players.add(new PlayerEntry(buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean(), buffer.readUnsignedByte()));
        }
        return new PolicySnapshot(menuId, owner, name, revision, status, players);
    }
}
