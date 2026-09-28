package dev.ae2security.network;

import dev.ae2security.MESecurity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PolicyEditResult(int menuId, int requestId, Result result) implements CustomPacketPayload {
    public enum Result { APPLIED, REFRESH, DENIED, INVALID }
    public static final Type<PolicyEditResult> TYPE = new Type<>(MESecurity.id("policy_edit_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PolicyEditResult> CODEC = StreamCodec.of(
            (buffer, result) -> {
                buffer.writeVarInt(result.menuId());
                buffer.writeVarInt(result.requestId());
                buffer.writeEnum(result.result());
            }, buffer -> new PolicyEditResult(buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readEnum(Result.class)));
    @Override public Type<PolicyEditResult> type() { return TYPE; }
}
