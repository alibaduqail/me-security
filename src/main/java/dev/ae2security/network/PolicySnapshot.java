package dev.ae2security.network;

import java.util.UUID;
import dev.ae2security.MESecurity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PolicySnapshot(int menuId, UUID owner, String ownerName, long revision, String status,
        long directoryRevision) implements CustomPacketPayload {
    public record PlayerEntry(UUID id, String name, boolean trusted, int permissions) {}
    public static final Type<PolicySnapshot> TYPE = new Type<>(MESecurity.id("policy_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PolicySnapshot> CODEC = StreamCodec.of(
            PolicySnapshot::write, PolicySnapshot::read);

    @Override public Type<PolicySnapshot> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buffer, PolicySnapshot snapshot) {
        buffer.writeVarInt(snapshot.menuId());
        buffer.writeUUID(snapshot.owner());
        buffer.writeUtf(snapshot.ownerName(), 64);
        buffer.writeLong(snapshot.revision());
        buffer.writeUtf(snapshot.status(), 64);
        buffer.writeLong(snapshot.directoryRevision());
    }

    private static PolicySnapshot read(RegistryFriendlyByteBuf buffer) {
        int menuId = buffer.readVarInt();
        UUID owner = buffer.readUUID();
        String name = buffer.readUtf(64);
        long revision = buffer.readLong();
        String status = buffer.readUtf(64);
        return new PolicySnapshot(menuId, owner, name, revision, status, buffer.readLong());
    }
}
