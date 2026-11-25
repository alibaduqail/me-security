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
public final class ProtectedCellHandler implements IItemHandler {
    private final AEBaseInvBlockEntity host;
    private final IItemHandler delegate;
    private final IntPredicate cellSlot;
    /** Server tick of the cached answer, or -1 when there is none. */
    private long cachedTick = -1;
    private boolean cachedAllowed;

    public ProtectedCellHandler(AEBaseInvBlockEntity host, IItemHandler delegate, IntPredicate cellSlot) {
        this.host = host;
        this.delegate = delegate;
        this.cellSlot = cellSlot;
    }

    public AEBaseInvBlockEntity host() { return host; }
    public IItemHandler delegate() { return delegate; }
    public IntPredicate cellSlot() { return cellSlot; }

    public static IItemHandler wrap(AEBaseInvBlockEntity host, Direction side, IItemHandler handler) {
        if (handler == null || !SecurityHardware.nativeHost(host)) return handler;
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
        if (!cellSlot.test(slot)) return true;
        // Stale references are rejected on every call: a removed or replaced block entity never passes.
        var level = host.getLevel();
        if (level == null || level.isClientSide() || host.isRemoved()
                || level.getBlockEntity(host.getBlockPos()) != host) {
            cachedTick = -1;
            return false;
        }
        // Rebuilding the network membership is costly and hoppers query many slots per transfer, so the
        // answer is computed once per game tick. A security change may therefore take effect one tick late.
        long now = level.getGameTime();
        if (now != cachedTick) {
            cachedAllowed = SecurityHardware.cellAutomationAllowed(host);
            cachedTick = now;
        }
        return cachedAllowed;
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
