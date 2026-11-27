package dev.ae2security.network;

import dev.ae2security.MESecurity;
import dev.ae2security.security.AlarmMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Carries intent only, like {@link EditPolicy}: the open menu and the server player supply the authority. */
public record SetAlarm(int menuId, AlarmMode mode) implements CustomPacketPayload {
    public static final Type<SetAlarm> TYPE = new Type<>(MESecurity.id("set_alarm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetAlarm> CODEC = StreamCodec.of(
            (buffer, request) -> {
                buffer.writeVarInt(request.menuId());
                buffer.writeEnum(request.mode());
            }, buffer -> new SetAlarm(buffer.readVarInt(), buffer.readEnum(AlarmMode.class)));
    @Override public Type<SetAlarm> type() { return TYPE; }
}
