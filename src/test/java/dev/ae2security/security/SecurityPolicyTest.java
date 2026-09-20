package dev.ae2security.security;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SecurityPolicyTest {
    static final UUID OWNER = new UUID(0, 1), TRUSTED = new UUID(0, 2), OUTSIDER = new UUID(0, 3);
    static IntStream masks() { return IntStream.rangeClosed(0, Permission.ALL); }

    @ParameterizedTest @MethodSource("masks")
    void everyPermissionCombination(int mask) {
        var policy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, mask);
        for (var permission : Permission.values()) {
            boolean expected = permission == Permission.VIEW || (mask & permission.bit()) != 0;
            assertEquals(expected, policy.allows(TRUSTED, permission));
            assertTrue(policy.allows(OWNER, permission));
            assertFalse(policy.allows(OUTSIDER, permission));
        }
    }
    @Test void trustingAlwaysGrantsView() {
        var policy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, 0);
        assertTrue(policy.allows(TRUSTED, Permission.VIEW));
        assertEquals(Permission.VIEW.bit(), policy.permissions(TRUSTED));
        assertEquals(List.of(Permission.INSERT, Permission.EXTRACT, Permission.CRAFT, Permission.BUILD),
                Permission.editable());
    }
    @Test void ownerIsNotAnEditableTrustedEntry() {
        var policy = SecurityPolicy.create(OWNER);
        assertThrows(IllegalArgumentException.class, () -> policy.trust(OWNER, OWNER, 0));
        assertEquals(Permission.ALL, new SecurityPolicy(OWNER, Map.of(OWNER, 0), 0).permissions(OWNER));
    }
    @Test void allMutationsRequireTheCurrentOwner() {
        var policy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, Permission.ALL);
        assertThrows(SecurityException.class, () -> policy.trust(TRUSTED, OUTSIDER, Permission.ALL));
        assertThrows(SecurityException.class, () -> policy.remove(OUTSIDER, TRUSTED));
        assertThrows(SecurityException.class, () -> policy.transfer(TRUSTED, TRUSTED));
    }
    @Test void revocationDoesNotAlterOldSnapshots() {
        var before = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, Permission.ALL);
        var after = before.remove(OWNER, TRUSTED);
        assertTrue(before.allows(TRUSTED, Permission.EXTRACT));
        assertFalse(after.allows(TRUSTED, Permission.VIEW));
        assertEquals(before.revision() + 1, after.revision());
    }
    @Test void transferKeepsOtherTrustButRemovesFormerOwnerAccess() {
        var before = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, Permission.ALL)
                .trust(OWNER, OUTSIDER, Permission.VIEW.bit());
        var after = before.transfer(OWNER, TRUSTED);
        assertEquals(TRUSTED, after.owner());
        assertEquals(0, after.permissions(OWNER));
        assertEquals(1, after.permissions(OUTSIDER));
        assertFalse(after.trusted().containsKey(TRUSTED));
        assertThrows(SecurityException.class, () -> after.trust(OWNER, OWNER, Permission.ALL));
    }
    @Test void craftDoesNotGrantExtractionOrInsertion() {
        var policy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, Permission.VIEW.bit() | Permission.CRAFT.bit());
        assertTrue(policy.allows(TRUSTED, Permission.CRAFT));
        assertFalse(policy.allows(TRUSTED, Permission.EXTRACT));
        assertFalse(policy.allows(TRUSTED, Permission.INSERT));
        assertFalse(policy.allows(TRUSTED, Permission.BUILD));
    }
    @Test void buildDoesNotGrantStorageOrCraftingAccess() {
        var policy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, Permission.VIEW.bit() | Permission.BUILD.bit());
        assertTrue(policy.allows(TRUSTED, Permission.BUILD));
        assertFalse(policy.allows(TRUSTED, Permission.INSERT));
        assertFalse(policy.allows(TRUSTED, Permission.EXTRACT));
        assertFalse(policy.allows(TRUSTED, Permission.CRAFT));
    }
    @Test void constructorCopiesItsInputAndMasksFutureBits() {
        var entries = new HashMap<UUID, Integer>();
        entries.put(TRUSTED, 255);
        var policy = new SecurityPolicy(OWNER, entries, 0);
        entries.clear();
        assertEquals(Permission.ALL, policy.permissions(TRUSTED));
        assertThrows(UnsupportedOperationException.class, () -> policy.trusted().clear());
    }
}
