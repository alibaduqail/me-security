package dev.ae2security.network;

import dev.ae2security.MESecurity;
import dev.ae2security.security.AlarmMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Tells the owner's open screen which alarm mode the terminal is in. */
public record AlarmState(int menuId, AlarmMode mode) implements CustomPacketPayload {
    public static final Type<AlarmState> TYPE = new Type<>(MESecurity.id("alarm_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlarmState> CODEC = StreamCodec.of(
            (buffer, state) -> {
                buffer.writeVarInt(state.menuId());
                buffer.writeEnum(state.mode());
            }, buffer -> new AlarmState(buffer.readVarInt(), buffer.readEnum(AlarmMode.class)));
    @Override public Type<AlarmState> type() { return TYPE; }
}
