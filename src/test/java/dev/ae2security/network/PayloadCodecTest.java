package dev.ae2security.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;

class PayloadCodecTest {
    private static <T> T roundTrip(T value, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    void terminalPayloadsRoundTripWithRequestIdentity() {
        var owner = UUID.randomUUID();
        var target = UUID.randomUUID();
        assertEquals(new EditPolicy(4, 31, 900L, target, EditPolicy.Action.TRANSFER, 0, "Player"),
                roundTrip(new EditPolicy(4, 31, 900L, target, EditPolicy.Action.TRANSFER, 0, "Player"),
                        EditPolicy.CODEC));
        var policy = new PolicySnapshot(4, owner, "Owner", 900L, "protected", 12345L);
        assertEquals(policy, roundTrip(policy, PolicySnapshot.CODEC));
        var editResult = new PolicyEditResult(4, 31, PolicyEditResult.Result.REFRESH);
        assertEquals(editResult, roundTrip(editResult, PolicyEditResult.CODEC));
        var search = new PlayerSearchRequest(4, 72, target.toString(), 1400);
        assertEquals(search, roundTrip(search, PlayerSearchRequest.CODEC));
        var page = new PlayerSearchResult(4, 72, 1400, 9001, false,
                List.of(new PolicySnapshot.PlayerEntry(target, "Guest", true, 31)));
        assertEquals(page, roundTrip(page, PlayerSearchResult.CODEC));
    }

    @Test
    void rejectsOversizedPlayerPageBeforeReadingEntries() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        try {
            buffer.writeVarInt(4);
            buffer.writeVarInt(1);
            buffer.writeVarInt(0);
            buffer.writeVarInt(100);
            buffer.writeBoolean(false);
            buffer.writeVarInt(PlayerSearchResult.PAGE_SIZE + 1);
            assertThrows(IllegalArgumentException.class, () -> PlayerSearchResult.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
