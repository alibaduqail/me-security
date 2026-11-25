package dev.ae2security.security;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkKeyTest {
    @Test void validDimensionParsesOnceAndIsShared() {
        var a = new SecuritySavedData.ChunkKey("minecraft:the_nether", 1, 2);
        var b = new SecuritySavedData.ChunkKey("minecraft:the_nether", 9, 9);
        assertEquals(ResourceLocation.parse("minecraft:the_nether"), a.dimensionKey().location());
        assertSame(a.dimensionKey(), b.dimensionKey());
    }

    @Test void malformedDimensionHasNoKey() {
        assertNull(new SecuritySavedData.ChunkKey("Not A Dimension!", 0, 0).dimensionKey());
        assertNull(new SecuritySavedData.ChunkKey("minecraft:Bad Path", 0, 0).dimensionKey());
    }

    @Test void equalityIgnoresTheCache() {
        var a = new SecuritySavedData.ChunkKey("minecraft:overworld", 3, -2);
        a.dimensionKey();
        var b = new SecuritySavedData.ChunkKey("minecraft:overworld", 3, -2);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new SecuritySavedData.ChunkKey("minecraft:overworld", 3, -1));
        assertNotEquals(a, new SecuritySavedData.ChunkKey("minecraft:the_end", 3, -2));
    }

    @Test void serializationRoundTripKeepsDimensionStrings() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        data.create(terminal, UUID.randomUUID());
        var valid = new SecuritySavedData.ChunkKey("minecraft:overworld", 3, -2);
        var bad = new SecuritySavedData.ChunkKey("Not A Dimension!", 0, 1);
        data.reconcileChunks(terminal, Set.of(valid, bad), chunk -> false);
        var restored = SecuritySavedData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(Set.of(valid, bad), restored.footprint(terminal));
    }
}
