package dev.ae2security.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

class PayloadCodecTest {
    private static final int MAX_TEXT = 64;
    private static final String PROTOCOL_VERSION = "5";

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
    void candidatePayloadsRoundTripIncludingRequestIdentity() {
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

    private static RegistryFriendlyByteBuf newBuffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    /** Writes the wire form by hand so the text is not limited by the codec's own encoder. */
    private static void writeEdit(RegistryFriendlyByteBuf buffer, String confirmation) {
        buffer.clear();
        buffer.writeVarInt(4);
        buffer.writeVarInt(31);
        buffer.writeLong(900L);
        buffer.writeUUID(new UUID(1, 2));
        buffer.writeEnum(EditPolicy.Action.TRANSFER);
        buffer.writeByte(0);
        buffer.writeUtf(confirmation);
    }

    private static void writeSearch(RegistryFriendlyByteBuf buffer, String query) {
        buffer.clear();
        buffer.writeVarInt(4);
        buffer.writeVarInt(72);
        buffer.writeUtf(query);
        buffer.writeVarInt(0);
    }

    @Test
    void editPolicyRejectsOverLengthConfirmation() {
        var buffer = newBuffer();
        try {
            writeEdit(buffer, "n".repeat(MAX_TEXT));
            assertEquals(MAX_TEXT, EditPolicy.CODEC.decode(buffer).confirmation().length());
            writeEdit(buffer, "n".repeat(MAX_TEXT + 1));
            assertThrows(DecoderException.class, () -> EditPolicy.CODEC.decode(buffer));
            writeEdit(buffer, "n".repeat(4_096));
            assertThrows(DecoderException.class, () -> EditPolicy.CODEC.decode(buffer));
            buffer.clear();
            assertThrows(EncoderException.class, () -> EditPolicy.CODEC.encode(buffer, new EditPolicy(4, 31, 900L,
                    new UUID(1, 2), EditPolicy.Action.TRANSFER, 0, "n".repeat(MAX_TEXT + 1))));
        } finally {
            buffer.release();
        }
    }

    @Test
    void playerSearchRequestRejectsOverLengthQuery() {
        var buffer = newBuffer();
        try {
            writeSearch(buffer, "q".repeat(MAX_TEXT));
            assertEquals(new PlayerSearchRequest(4, 72, "q".repeat(MAX_TEXT), 0),
                    PlayerSearchRequest.CODEC.decode(buffer));
            writeSearch(buffer, "q".repeat(MAX_TEXT + 1));
            assertThrows(DecoderException.class, () -> PlayerSearchRequest.CODEC.decode(buffer));
            writeSearch(buffer, "q".repeat(4_096));
            assertThrows(DecoderException.class, () -> PlayerSearchRequest.CODEC.decode(buffer));
            buffer.clear();
            assertThrows(EncoderException.class, () -> PlayerSearchRequest.CODEC.encode(buffer,
                    new PlayerSearchRequest(4, 72, "q".repeat(MAX_TEXT + 1), 0)));
        } finally {
            buffer.release();
        }
    }

    /**
     * Pins the version handed to NeoForge's registrar. Changing any payload's wire format requires
     * bumping it, and bumping it locks out every client and server still on the old jar, so
     * either change has to be made here deliberately as well.
     */
    @Test
    void payloadProtocolVersionIsPinned() {
        var requested = new ArrayList<String>();
        var event = new RegisterPayloadHandlersEvent() {
            @Override
            public PayloadRegistrar registrar(String version) {
                requested.add(version);
                // Stop before anything reaches NeoForge's global, register-once payload table.
                throw new VersionCaptured();
            }
        };

        assertThrows(VersionCaptured.class, () -> SecurityPayloads.register(event));
        assertEquals(List.of(PROTOCOL_VERSION), requested);
    }

    private static final class VersionCaptured extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
