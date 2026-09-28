package dev.ae2security.network;

import dev.ae2security.MESecurity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlayerSearchRequest(int menuId, int sequence, String query, int page)
        implements CustomPacketPayload {
    public static final Type<PlayerSearchRequest> TYPE = new Type<>(MESecurity.id("player_search_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSearchRequest> CODEC = StreamCodec.of(
            (buffer, request) -> {
                buffer.writeVarInt(request.menuId());
                buffer.writeVarInt(request.sequence());
                buffer.writeUtf(request.query(), 64);
                buffer.writeVarInt(request.page());
            }, buffer -> new PlayerSearchRequest(buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(64), buffer.readVarInt()));
    @Override public Type<PlayerSearchRequest> type() { return TYPE; }
}
