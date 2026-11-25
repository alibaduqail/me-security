package dev.ae2security.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SecuritySavedDataTest {
    private static final String NAME = "ae2security_policies";

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
        data.reconcileChunks(terminal, Set.of(new SecuritySavedData.ChunkKey("minecraft:overworld", 3, -2)), chunk -> false);

        var serialized = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        var restored = SecuritySavedData.load(serialized, RegistryAccess.EMPTY);
        assertEquals(data.policy(terminal), restored.policy(terminal));
        assertTrue(restored.policy(terminal).allows(builder, Permission.BUILD));
        assertFalse(restored.policy(terminal).allows(builder, Permission.EXTRACT));
        assertEquals(data.players(), restored.players());
        assertEquals(data.footprint(terminal), restored.footprint(terminal));
        assertTrue(restored.retired(retired));
        assertNull(restored.policy(retired));
        assertEquals(serialized, restored.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertThrows(IllegalStateException.class, () -> restored.create(retired, owner));
    }

    /** One live terminal, one tombstone and one directory entry, as written to disk. */
    private static CompoundTag savedWorld() {
        var data = new SecuritySavedData();
        var owner = UUID.randomUUID();
        var removed = UUID.randomUUID();
        data.create(UUID.randomUUID(), owner);
        data.create(removed, owner);
        data.retire(removed);
        data.rememberPlayer(owner, "Owner");
        return data.save(new CompoundTag(), RegistryAccess.EMPTY);
    }

    private static void assertCorrupt(CompoundTag tag) {
        var error = assertThrows(SecuritySavedData.CorruptDataError.class,
                () -> SecuritySavedData.load(tag, RegistryAccess.EMPTY));
        assertTrue(error.getMessage().contains("data/" + NAME + ".dat"), "the message must name the file");
        assertTrue(error.getMessage().contains("backup"), "the message must say how to recover");
    }

    @Test void malformedPolicyOrTombstoneDataIsAnErrorNotAnEmptyInstance() {
        var noTerminal = savedWorld();
        noTerminal.getList("policies", Tag.TAG_COMPOUND).getCompound(0).remove("terminal");
        assertCorrupt(noTerminal);

        var noOwner = savedWorld();
        noOwner.getList("policies", Tag.TAG_COMPOUND).getCompound(0).getCompound("policy").remove("owner");
        assertCorrupt(noOwner);

        var noPolicy = savedWorld();
        noPolicy.getList("policies", Tag.TAG_COMPOUND).getCompound(0).remove("policy");
        assertCorrupt(noPolicy);

        var shortTombstone = savedWorld();
        shortTombstone.getList("retired", Tag.TAG_INT_ARRAY).set(0, new IntArrayTag(new int[] {1, 2}));
        assertCorrupt(shortTombstone);

        var wrongKind = savedWorld();
        wrongKind.putString("policies", "not a list");
        assertCorrupt(wrongKind);

        // Exception is what Minecraft swallows; the failure has to be something else.
        assertFalse(Exception.class.isAssignableFrom(SecuritySavedData.CorruptDataError.class));
    }

    @Test void unreadableFileNeverBecomesAFreshSaveableInstance(@TempDir Path folder) throws Exception {
        var file = folder.resolve(NAME + ".dat");
        var malformed = savedWorld();
        malformed.getList("policies", Tag.TAG_COMPOUND).getCompound(0).remove("terminal");
        var root = new CompoundTag();
        root.put("data", malformed);
        NbtIo.writeCompressed(root, file);
        byte[] original = Files.readAllBytes(file);
        assertThrows(SecuritySavedData.CorruptDataError.class, () -> storage(folder)
                .computeIfAbsent(SecuritySavedData.factory(() -> file), NAME));
        assertArrayEquals(original, Files.readAllBytes(file), "a failed load must leave the file as it was");

        Files.writeString(file, "this is not NBT");
        assertThrows(SecuritySavedData.CorruptDataError.class, () -> storage(folder)
                .computeIfAbsent(SecuritySavedData.factory(() -> file), NAME));

        root.put("data", savedWorld());
        NbtIo.writeCompressed(root, file);
        var loaded = storage(folder).computeIfAbsent(SecuritySavedData.factory(() -> file), NAME);
        assertEquals(1, loaded.players().size(), "a readable file still loads through the same path");

        Files.delete(file);
        var fresh = storage(folder).computeIfAbsent(SecuritySavedData.factory(() -> file), NAME);
        assertTrue(fresh.players().isEmpty(), "a world without the file starts empty");
    }

    private static DimensionDataStorage storage(Path folder) {
        // The data fixer is only consulted for vanilla data types; this one has none.
        return new DimensionDataStorage(folder.toFile(), null, RegistryAccess.EMPTY);
    }

    @Test void badDirectoryAndAllowanceRowsAreSkipped() {
        var tag = savedWorld();
        var known = tag.getList("players", Tag.TAG_COMPOUND);
        var good = known.getCompound(0).copy();
        var noId = new CompoundTag();
        noId.putString("name", "Nobody");
        known.add(noId);
        var noName = new CompoundTag();
        noName.putUUID("id", UUID.randomUUID());
        known.add(noName);
        var outputs = tag.getList("craftedOutputs", Tag.TAG_COMPOUND);
        outputs.add(new CompoundTag());

        var restored = SecuritySavedData.load(tag, RegistryAccess.EMPTY);
        assertEquals(1, restored.players().size());
        assertEquals("Owner", restored.name(good.getUUID("id")));
        assertEquals(1, restored.save(new CompoundTag(), RegistryAccess.EMPTY)
                .getList("policies", Tag.TAG_COMPOUND).size(), "policies still load beside a bad directory row");
    }

    @Test void directoryIsAnsweredWithoutCopyingIt() {
        var data = new SecuritySavedData();
        var seen = UUID.randomUUID();
        var later = UUID.randomUUID();
        data.rememberPlayer(seen, "Seen");
        assertTrue(data.knows(seen));
        assertFalse(data.knows(later));

        var view = data.players();
        assertSame(view, data.players(), "players() must not build a new map per call");
        data.rememberPlayer(later, "Later");
        assertEquals("Later", view.get(later), "the view follows the directory");
        assertTrue(data.knows(later));
        assertThrows(UnsupportedOperationException.class, () -> view.put(UUID.randomUUID(), "Intruder"));
    }

    @Test void allowancesEndWithTheAccessThatEarnedThem() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        var elsewhere = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var crafter = UUID.randomUUID();
        var keeper = UUID.randomUUID();
        var diamond = AEItemKey.of(Items.DIAMOND);
        data.create(terminal, owner);
        data.create(elsewhere, owner);
        data.update(terminal, data.policy(terminal).trust(owner, crafter, Permission.CRAFT.bit())
                .trust(owner, keeper, Permission.CRAFT.bit()));
        data.update(elsewhere, data.policy(elsewhere).trust(owner, crafter, Permission.CRAFT.bit()));
        data.recordCraftedOutput(terminal, crafter, new GenericStack(diamond, 5));
        data.recordCraftedOutput(terminal, keeper, new GenericStack(diamond, 2));
        data.recordCraftedOutput(terminal, owner, new GenericStack(diamond, 1));
        data.recordCraftedOutput(elsewhere, crafter, new GenericStack(diamond, 7));

        data.update(terminal, data.policy(terminal).trust(owner, crafter, Permission.VIEW.bit()));
        assertEquals(5, data.craftedAmount(terminal, crafter, diamond), "a permission change keeps the claim");

        data.update(terminal, data.policy(terminal).remove(owner, crafter));
        assertEquals(0, data.craftedAmount(terminal, crafter, diamond), "removing trust drops the claim");
        assertEquals(2, data.craftedAmount(terminal, keeper, diamond), "other players keep theirs");
        assertEquals(7, data.craftedAmount(elsewhere, crafter, diamond), "other terminals are untouched");

        data.recordCraftedOutput(terminal, crafter, new GenericStack(diamond, 3));
        assertEquals(0, data.craftedAmount(terminal, crafter, diamond), "a job finishing after removal banks nothing");
        data.update(terminal, data.policy(terminal).trust(owner, crafter, Permission.CRAFT.bit()));
        assertEquals(0, data.craftedAmount(terminal, crafter, diamond), "trusting again grants nothing back");

        data.update(terminal, data.policy(terminal).transfer(owner, keeper));
        assertEquals(0, data.craftedAmount(terminal, owner, diamond), "a transfer drops the old owner's claim");
        assertEquals(2, data.craftedAmount(terminal, keeper, diamond), "the new owner keeps their own");
    }

    private static SecuritySavedData.ChunkKey chunk(int x, int z) {
        return new SecuritySavedData.ChunkKey("minecraft:overworld", x, z);
    }

    @Test void reconcileRemembersTheFootprintAndForgetsOnlyAcceptedChunksOutsideIt() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        data.create(terminal, UUID.randomUUID());
        assertTrue(data.reconcileChunks(terminal, Set.of(chunk(0, 0), chunk(1, 0), chunk(2, 0)), c -> false));
        assertEquals(Set.of(chunk(0, 0), chunk(1, 0), chunk(2, 0)), data.footprint(terminal));

        var consulted = new java.util.HashSet<SecuritySavedData.ChunkKey>();
        var current = Set.of(chunk(0, 0), chunk(5, 5));
        assertTrue(data.reconcileChunks(terminal, current, c -> consulted.add(c) && c.equals(chunk(1, 0))
                || c.equals(chunk(0, 0))));
        assertEquals(Set.of(chunk(0, 0), chunk(2, 0), chunk(5, 5)), data.footprint(terminal),
                "current chunks are added and kept even when accepted; only accepted chunks outside it go");
        assertEquals(Set.of(chunk(1, 0), chunk(2, 0)), consulted, "only chunks outside the footprint are consulted");

        assertTrue(data.reconcileChunks(terminal, Set.of(chunk(5, 5)), c -> true));
        assertEquals(Set.of(chunk(5, 5)), data.footprint(terminal), "a provable footprint shrinks to the network");
    }

    @Test void reconcileWritesOnlyWhenTheRememberedSetChanges() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        data.create(terminal, UUID.randomUUID());
        data.reconcileChunks(terminal, Set.of(chunk(0, 0), chunk(1, 1)), c -> false);
        data.setDirty(false);
        long revision = data.footprintRevision();

        assertFalse(data.reconcileChunks(terminal, Set.of(chunk(0, 0), chunk(1, 1)), c -> {
            throw new AssertionError("nothing outside the footprint to consult");
        }));
        assertFalse(data.reconcileChunks(terminal, Set.of(chunk(0, 0)), c -> false), "an unprovable chunk stays");
        assertFalse(data.isDirty(), "an unchanged footprint is not written");
        assertEquals(revision, data.footprintRevision());
        assertEquals(Set.of(chunk(0, 0), chunk(1, 1)), data.footprint(terminal));

        assertTrue(data.reconcileChunks(terminal, Set.of(chunk(0, 0)), c -> true));
        assertTrue(data.isDirty());
        assertNotEquals(revision, data.footprintRevision(), "a change is visible through the revision");
        assertThrows(UnsupportedOperationException.class, () -> data.footprint(terminal).add(chunk(9, 9)));
    }

    @Test void retiredTerminalsKeepNoFootprint() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        data.create(terminal, UUID.randomUUID());
        data.reconcileChunks(terminal, Set.of(chunk(0, 0)), c -> false);
        long revision = data.footprintRevision();
        data.retire(terminal);
        assertTrue(data.footprint(terminal).isEmpty());
        assertNotEquals(revision, data.footprintRevision(), "retiring changes what callers last saw");
        assertFalse(data.reconcileChunks(terminal, Set.of(chunk(0, 0)), c -> false));
        assertTrue(data.footprint(terminal).isEmpty(), "a retired terminal does not start remembering again");
    }

    @Test void prunedFootprintSurvivesSaveAndLoadInTheSameFormat() {
        var data = new SecuritySavedData();
        var terminal = UUID.randomUUID();
        data.create(terminal, UUID.randomUUID());
        data.reconcileChunks(terminal, Set.of(chunk(0, 0), chunk(7, -3),
                new SecuritySavedData.ChunkKey("minecraft:the_nether", 1, 2)), c -> false);
        data.reconcileChunks(terminal, Set.of(chunk(0, 0)), c -> c.x() == 7);
        var saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertEquals(3, saved.getInt("format"));
        var restored = SecuritySavedData.load(saved, RegistryAccess.EMPTY);
        assertEquals(Set.of(chunk(0, 0), new SecuritySavedData.ChunkKey("minecraft:the_nether", 1, 2)),
                restored.footprint(terminal));
    }
}
