package dev.ae2security.security;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.ae2security.security.SecurityPolicyTest.*;

class PolicyNbtTest {
    @Test void roundTripsOwnershipPermissionsAndRevision() {
        var policy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, 9).trust(OWNER, OUTSIDER, 3);
        assertEquals(policy, PolicyNbt.read(PolicyNbt.write(policy)));
    }
    @Test void roundTripsCollect() {
        var policy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, Permission.ALL)
                .trust(OWNER, OUTSIDER, Permission.VIEW.bit() | Permission.COLLECT.bit());
        var restored = PolicyNbt.read(PolicyNbt.write(policy));
        assertEquals(policy, restored);
        assertTrue(restored.allows(OUTSIDER, Permission.COLLECT));
        assertFalse(restored.allows(OUTSIDER, Permission.CRAFT));
    }
    @Test void legacyReadGrantsCollectToEveryEntryWithCraft() {
        var craftOnly = Permission.VIEW.bit() | Permission.CRAFT.bit();
        var other = new java.util.UUID(0, 9);
        var legacy = SecurityPolicy.create(OWNER).trust(OWNER, TRUSTED, craftOnly)
                .trust(OWNER, OUTSIDER, Permission.EXTRACT.bit()).trust(OWNER, other, Permission.BUILD.bit());
        var tag = PolicyNbt.write(legacy);
        var plain = PolicyNbt.read(tag);
        assertEquals(legacy, plain, "the default read never migrates");
        var migrated = PolicyNbt.read(tag, true);
        assertEquals(craftOnly | Permission.COLLECT.bit(), migrated.permissions(TRUSTED));
        assertEquals(Permission.VIEW.bit() | Permission.EXTRACT.bit(), migrated.permissions(OUTSIDER));
        assertEquals(Permission.VIEW.bit() | Permission.BUILD.bit(), migrated.permissions(other));
        assertEquals(legacy.revision(), migrated.revision());
    }
    @Test void corruptOwnerIsAnErrorRatherThanAPublicPolicy() {
        assertThrows(IllegalArgumentException.class, () -> PolicyNbt.read(new CompoundTag()));
    }
}
