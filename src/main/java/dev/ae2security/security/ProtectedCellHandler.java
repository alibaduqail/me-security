package dev.ae2security.security;

import java.util.function.IntPredicate;

import appeng.blockentity.AEBaseInvBlockEntity;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.blockentity.spatial.SpatialIOPortBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Guards the original exposed slot mapping, even when another mod caches a public handler. */
public record ProtectedCellHandler(AEBaseInvBlockEntity host, IItemHandler delegate, IntPredicate cellSlot)
        implements IItemHandler {
    public static IItemHandler wrap(AEBaseInvBlockEntity host, Direction side, IItemHandler handler) {
        // Add-on hosts are wrapped only when they extend one of these AE2 classes and inherit its slot layout.
        if (handler == null || !SecurityHardware.gridHost(host)) return handler;
        if (host instanceof MEChestBlockEntity chest) {
            // Unsided access combines input slot 0 and cell slot 1. The front exposes only the cell.
            return new ProtectedCellHandler(host, handler, side == null ? slot -> slot == 1
                    : side == chest.getFront() ? slot -> true : slot -> false);
        }
        // A Quantum Link Chamber's singularity is what holds the two halves of the network together.
        if (host instanceof DriveBlockEntity || host instanceof IOPortBlockEntity || host instanceof SpatialIOPortBlockEntity
                || host instanceof QuantumBridgeBlockEntity) {
            return new ProtectedCellHandler(host, handler, slot -> true);
        }
        return handler;
    }

    private boolean allowed(int slot) {
        return !cellSlot.test(slot) || SecurityHardware.cellAutomationAllowed(host);
    }

    @Override public int getSlots() { return delegate.getSlots(); }
    @Override public ItemStack getStackInSlot(int slot) { return allowed(slot) ? delegate.getStackInSlot(slot) : ItemStack.EMPTY; }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return allowed(slot) ? delegate.insertItem(slot, stack, simulate) : stack;
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return allowed(slot) ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }
    @Override public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return allowed(slot) && delegate.isItemValid(slot, stack); }
}
