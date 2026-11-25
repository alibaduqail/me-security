package dev.ae2security.security;

import java.util.UUID;
import dev.ae2security.security.TerminalIdentity.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerminalIdentityTest {
    private static final TerminalLocation ORIGINAL =
            new TerminalLocation("minecraft:overworld", new BlockPos(10, 64, -3), Direction.NORTH);
    private static final TerminalLocation ELSEWHERE =
            new TerminalLocation("minecraft:overworld", new BlockPos(200, 70, 40), Direction.NORTH);

    @Test void unknownLocationIsClaimedWhateverTheWorldShows() {
        for (boolean loaded : new boolean[] {false, true}) {
            for (boolean holds : new boolean[] {false, true}) {
                assertEquals(Placement.RECORD, TerminalIdentity.classify(null, ELSEWHERE, loaded, holds));
                assertTrue(TerminalIdentity.shouldRetire(null, ELSEWHERE, loaded, holds));
            }
        }
    }

    @Test void sameLocationIsCanonical() {
        var reloaded = new TerminalLocation("minecraft:overworld", new BlockPos(10, 64, -3), Direction.NORTH);
        assertEquals(Placement.SAME, TerminalIdentity.classify(ORIGINAL, reloaded, false, false));
        assertTrue(TerminalIdentity.shouldRetire(ORIGINAL, reloaded, false, false));
    }

    @Test void loadedOriginalStillHoldingTheIdentityMakesACopy() {
        assertEquals(Placement.CLONE, TerminalIdentity.classify(ORIGINAL, ELSEWHERE, true, true));
        assertFalse(TerminalIdentity.shouldRetire(ORIGINAL, ELSEWHERE, true, true),
                "removing a copy must never retire the identity its original still carries");
    }

    @Test void vacatedOriginalMeansTheTerminalMoved() {
        assertEquals(Placement.MOVE, TerminalIdentity.classify(ORIGINAL, ELSEWHERE, true, false));
        assertTrue(TerminalIdentity.shouldRetire(ORIGINAL, ELSEWHERE, true, false));
    }

    @Test void unloadedOriginalIsAmbiguousAndNeverRetired() {
        for (boolean holds : new boolean[] {false, true}) {
            assertEquals(Placement.AMBIGUOUS, TerminalIdentity.classify(ORIGINAL, ELSEWHERE, false, holds));
            assertFalse(TerminalIdentity.shouldRetire(ORIGINAL, ELSEWHERE, false, holds));
        }
    }

    @Test void anotherSideOrDimensionIsAnotherLocation() {
        var otherSide = new TerminalLocation("minecraft:overworld", ORIGINAL.pos(), Direction.SOUTH);
        var otherDimension = new TerminalLocation("minecraft:the_nether", ORIGINAL.pos(), Direction.NORTH);
        assertEquals(Placement.CLONE, TerminalIdentity.classify(ORIGINAL, otherSide, true, true));
        assertEquals(Placement.MOVE, TerminalIdentity.classify(ORIGINAL, otherDimension, true, false));
    }

    @Test void locationRoundTripsAndRejectsIncompleteTags() {
        assertEquals(ORIGINAL, TerminalLocation.read(ORIGINAL.write()));
        var noSide = ORIGINAL.write();
        noSide.remove("side");
        assertNull(TerminalLocation.read(noSide));
        var badSide = ORIGINAL.write();
        badSide.putString("side", "sideways");
        assertNull(TerminalLocation.read(badSide));
        var badCoordinate = ORIGINAL.write();
        badCoordinate.putString("y", "high");
        assertNull(TerminalLocation.read(badCoordinate));
        assertNull(TerminalLocation.read(new CompoundTag()));
    }

    @Test void savedDataKeepsLocationsAsOptionalPolicyFields() {
        var data = new SecuritySavedData();
        var located = UUID.randomUUID();
        var legacy = UUID.randomUUID();
        var owner = UUID.randomUUID();
        data.create(located, owner);
        data.create(legacy, owner);
        data.setLocation(located, ORIGINAL);
        data.setLocation(UUID.randomUUID(), ELSEWHERE);

        var serialized = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertEquals(3, serialized.getInt("format"), "locations must not change the saved-data format");
        var restored = SecuritySavedData.load(serialized, RegistryAccess.EMPTY);
        assertEquals(ORIGINAL, restored.location(located));
        assertNull(restored.location(legacy), "a policy without a location stays unknown");
        assertEquals(serialized, restored.save(new CompoundTag(), RegistryAccess.EMPTY));

        restored.retire(located);
        assertNull(restored.location(located), "retirement forgets the location");
        restored.setLocation(located, ELSEWHERE);
        assertNull(restored.location(located), "a retired identity cannot be given a location again");
    }

    @Test void unreadableLocationIsIgnoredInsteadOfFailingTheWorld() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        data.create(terminal, UUID.randomUUID());
        var serialized = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        var row = serialized.getList("policies", 10).getCompound(0);
        row.put("location", IntTag.valueOf(7));
        var restored = assertDoesNotThrow(() -> SecuritySavedData.load(serialized, RegistryAccess.EMPTY));
        assertNotNull(restored.policy(terminal));
        assertNull(restored.location(terminal));
    }

    @Test void copiedPolicyStartsAtRevisionZero() {
        var data = new SecuritySavedData();
        var owner = UUID.randomUUID();
        var friend = UUID.randomUUID();
        var source = SecurityPolicy.create(owner).trust(owner, friend, Permission.EXTRACT.bit());
        var copy = UUID.randomUUID();
        data.create(copy, new SecurityPolicy(source.owner(), source.trusted(), 0));
        assertEquals(source.trusted(), data.policy(copy).trusted());
        assertEquals(0, data.policy(copy).revision());
        assertThrows(IllegalStateException.class, () -> data.create(copy, source));
    }

    @Test void inWorldRegistryTracksEachLocationOnce() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        data.markInWorld(terminal, ORIGINAL);
        data.markInWorld(terminal, ELSEWHERE);
        data.markInWorld(terminal, ORIGINAL);
        assertEquals(2, data.inWorldLocations(terminal).size());
        data.markOutOfWorld(terminal, ORIGINAL);
        assertEquals(java.util.List.of(ELSEWHERE), data.inWorldLocations(terminal));
        data.markOutOfWorld(terminal, ELSEWHERE);
        assertTrue(data.inWorldLocations(terminal).isEmpty());
    }
}
