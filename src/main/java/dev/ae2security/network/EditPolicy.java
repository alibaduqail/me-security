package dev.ae2security.network;

import java.util.UUID;
import dev.ae2security.MESecurity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Carries intent only. The current menu and authenticated server player supply the authority. */
public record EditPolicy(int menuId, long revision, UUID target, Action action, int permissions,
        String confirmation)
        implements CustomPacketPayload {
    public enum Action { TRUST, PERMISSIONS, REMOVE, TRANSFER, TOGGLE_PERMISSION }
    public static final Type<EditPolicy> TYPE = new Type<>(MESecurity.id("edit_policy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditPolicy> CODEC = StreamCodec.of(
            (buffer, edit) -> {
                buffer.writeVarInt(edit.menuId());
                buffer.writeLong(edit.revision());
                buffer.writeUUID(edit.target());
                buffer.writeEnum(edit.action());
                buffer.writeByte(edit.permissions());
                buffer.writeUtf(edit.confirmation(), 64);
            }, buffer -> new EditPolicy(buffer.readVarInt(), buffer.readLong(), buffer.readUUID(),
                    buffer.readEnum(Action.class), buffer.readUnsignedByte(), buffer.readUtf(64)));
    @Override public Type<EditPolicy> type() { return TYPE; }
}
