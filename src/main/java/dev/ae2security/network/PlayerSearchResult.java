package dev.ae2security.network;

import java.util.ArrayList;
import java.util.List;
import dev.ae2security.MESecurity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlayerSearchResult(int menuId, int sequence, int page, int total, boolean busy,
        List<PolicySnapshot.PlayerEntry> players) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 6;
    public static final Type<PlayerSearchResult> TYPE = new Type<>(MESecurity.id("player_search_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSearchResult> CODEC = StreamCodec.of(
            (buffer, result) -> {
                buffer.writeVarInt(result.menuId());
                buffer.writeVarInt(result.sequence());
                buffer.writeVarInt(result.page());
                buffer.writeVarInt(result.total());
                buffer.writeBoolean(result.busy());
                buffer.writeVarInt(result.players().size());
                for (var entry : result.players()) {
                    buffer.writeUUID(entry.id());
                    buffer.writeUtf(entry.name(), 64);
                    buffer.writeBoolean(entry.trusted());
                    buffer.writeByte(entry.permissions());
                }
            }, buffer -> {
                int menuId = buffer.readVarInt();
                int sequence = buffer.readVarInt();
                int page = buffer.readVarInt();
                int total = buffer.readVarInt();
                boolean busy = buffer.readBoolean();
                int count = buffer.readVarInt();
                if (page < 0 || total < 0 || count < 0 || count > PAGE_SIZE)
                    throw new IllegalArgumentException("Invalid player search result");
                var entries = new ArrayList<PolicySnapshot.PlayerEntry>(count);
                for (int i = 0; i < count; i++) entries.add(new PolicySnapshot.PlayerEntry(
                        buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean(), buffer.readUnsignedByte()));
                return new PlayerSearchResult(menuId, sequence, page, total, busy, entries);
            });
    public PlayerSearchResult { players = List.copyOf(players); }
    @Override public Type<PlayerSearchResult> type() { return TYPE; }
}
