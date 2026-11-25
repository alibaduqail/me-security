package dev.ae2security.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.security.Permission;
import dev.ae2security.security.PlayerMenuStorage;
import dev.ae2security.security.SecurityAccess;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Craft requests jobs; only Collect withdraws their completed outputs. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class CollectGameTests {
    private static final BlockPos TERMINAL_POS = BlockPos.ZERO;
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);

    private CollectGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void craftWithoutCollectCannotWithdrawCompletedOutput(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CollectOwner"));
        var crafter = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CollectCrafter"));
        var position = helper.absolutePos(TERMINAL_POS);
        PartHelper.setPart(level, position, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        var storageTerminal = PartHelper.setPart(level, position, Direction.SOUTH, owner, AEParts.TERMINAL.get());
        helper.setBlock(TERMINAL_POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(TERMINAL_POS.east(), AEBlocks.CRAFTING_STORAGE_1K.block());
        helper.runAfterDelay(30, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            trust(data, id, owner, crafter, Permission.CRAFT.bit());
            var grid = terminal.getGridNode().getGrid();
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(new TestStorage()));
            var network = grid.getStorageService().getInventory();
            var machine = IActionSource.ofMachine(terminal);
            var source = IActionSource.ofPlayer(crafter);
            network.insert(DIAMOND, 20, Actionable.MODULATE, machine);
            var cpu = ((CraftingBlockEntity) level.getBlockEntity(position.east())).getCluster();
            helper.assertTrue(cpu != null && cpu.isActive(), "crafting CPU must be active");
            var menuStorage = new PlayerMenuStorage(storageTerminal.getInventory(), storageTerminal, crafter);

            helper.assertTrue(cpu.submitJob(grid, emittedPlan(DIAMOND, 4), source, null).successful(),
                    "Craft alone submits a job");
            cpu.insert(DIAMOND, 4, Actionable.MODULATE, machine);
            helper.assertValueEqual(4L, data.craftedAmount(id, crafter.getUUID(), DIAMOND),
                    "a completed Craft-only job still records its allowance");
            helper.assertValueEqual(0L, SecurityAccess.extractionLimit(grid, source, DIAMOND, 4),
                    "Craft alone grants no withdrawal limit");
            helper.assertValueEqual(0L, network.extract(DIAMOND, 4, Actionable.MODULATE, source),
                    "Craft without Collect cannot withdraw the completed output");
            helper.assertValueEqual(0L, menuStorage.extract(DIAMOND, 4, Actionable.MODULATE, source),
                    "Craft without Collect cannot withdraw it through a terminal menu either");
            helper.assertValueEqual(4L, data.craftedAmount(id, crafter.getUUID(), DIAMOND),
                    "a refused withdrawal spends nothing");

            trust(data, id, owner, crafter, Permission.CRAFT.bit() | Permission.COLLECT.bit());
            helper.assertValueEqual(4L, network.extract(DIAMOND, 64, Actionable.SIMULATE, source),
                    "Craft and Collect see the quantity-limited allowance");
            helper.assertValueEqual(1L, menuStorage.extract(DIAMOND, 1, Actionable.MODULATE, source),
                    "Craft and Collect withdraw through a terminal menu");
            helper.assertValueEqual(3L, network.extract(DIAMOND, 64, Actionable.MODULATE, source),
                    "Craft and Collect withdraw only the remaining allowance");
            helper.assertValueEqual(0L, data.craftedAmount(id, crafter.getUUID(), DIAMOND), "the allowance is spent");
            helper.assertValueEqual(0L, network.extract(DIAMOND, 1, Actionable.MODULATE, source),
                    "the allowance cannot be reused");

            helper.assertTrue(cpu.submitJob(grid, emittedPlan(DIAMOND, 2), source, null).successful(),
                    "a second job submits");
            cpu.insert(DIAMOND, 2, Actionable.MODULATE, machine);
            trust(data, id, owner, crafter, Permission.COLLECT.bit());
            helper.assertValueEqual(2L, network.extract(DIAMOND, 64, Actionable.MODULATE, source),
                    "Collect collects an allowance earned under Craft even after Craft is removed");
            helper.assertFalse(cpu.submitJob(grid, emittedPlan(DIAMOND, 1), source, null).successful(),
                    "Collect alone cannot request a job");

            helper.destroyBlock(TERMINAL_POS.east());
            helper.destroyBlock(TERMINAL_POS.below());
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    private static void trust(SecuritySavedData data, UUID terminal, Player owner, Player player, int permissions) {
        data.update(terminal, data.policy(terminal).trust(owner.getUUID(), player.getUUID(), permissions));
    }

    private static ICraftingPlan emittedPlan(AEKey key, long amount) {
        return new ICraftingPlan() {
            @Override public GenericStack finalOutput() { return new GenericStack(key, amount); }
            @Override public long bytes() { return 1; }
            @Override public boolean simulation() { return false; }
            @Override public boolean multiplePaths() { return false; }
            @Override public KeyCounter usedItems() { return new KeyCounter(); }
            @Override public KeyCounter emittedItems() { var keys = new KeyCounter(); keys.add(key, amount); return keys; }
            @Override public KeyCounter missingItems() { return new KeyCounter(); }
            @Override public Map<IPatternDetails, Long> patternTimes() { return Map.of(); }
        };
    }

    private static final class TestStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();

        long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) amounts.merge(key, amount, Long::sum);
            return amount;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, amount(key));
            if (mode == Actionable.MODULATE && extracted > 0) amounts.merge(key, -extracted, Long::sum);
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            amounts.forEach(out::add);
        }

        @Override
        public Component getDescription() {
            return Component.literal("ME Security Collect GameTest storage");
        }
    }
}
