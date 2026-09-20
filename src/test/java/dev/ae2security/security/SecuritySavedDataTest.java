package dev.ae2security.security;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecuritySavedDataTest {
    @Test void worldDataPreservesPoliciesDirectoryAndRetirements() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        var retired = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var builder = UUID.randomUUID();
        data.create(terminal, owner);
        data.create(retired, owner);
        data.retire(retired);
        data.rememberPlayer(owner, "Owner");
        data.rememberPlayer(builder, "Guest");
        data.update(terminal, data.policy(terminal).trust(owner, builder, Permission.BUILD.bit()));
        data.rememberChunks(terminal, List.of(new SecuritySavedData.ChunkKey("minecraft:overworld", 3, -2)));

        var serialized = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        var restored = SecuritySavedData.load(serialized, RegistryAccess.EMPTY);
        assertEquals(data.policy(terminal), restored.policy(terminal));
        assertTrue(restored.policy(terminal).allows(builder, Permission.BUILD));
        assertFalse(restored.policy(terminal).allows(builder, Permission.EXTRACT));
        assertEquals(data.players(), restored.players());
        assertTrue(restored.retired(retired));
        assertNull(restored.policy(retired));
        assertEquals(serialized, restored.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertThrows(IllegalStateException.class, () -> restored.create(retired, owner));
    }
}
