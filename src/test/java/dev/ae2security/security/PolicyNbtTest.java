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
    @Test void corruptOwnerIsAnErrorRatherThanAPublicPolicy() {
        assertThrows(IllegalArgumentException.class, () -> PolicyNbt.read(new CompoundTag()));
    }
}
