package dev.ae2security.security;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Also covers ME chest menus, which access their cell directly rather than via NetworkStorage. */
public record PlayerMenuStorage(MEStorage delegate, Object host, Player player) implements MEStorage {
    private boolean allowed(Permission permission) {
        return SecurityAccess.allow(SecurityAccess.grid(host), player, permission, false);
    }
    @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return allowed(Permission.INSERT) ? delegate.insert(key, amount, mode, source) : 0;
    }
    @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        var grid = SecurityAccess.grid(host);
        if (grid == null) return allowed(Permission.EXTRACT) ? delegate.extract(key, amount, mode, source) : 0;
        // Menu access belongs to its player even if a caller supplied a machine action source.
        var playerSource = source.player().orElse(null) == player && !(source instanceof CraftingActionSource)
                ? source : IActionSource.ofPlayer(player);
        long limit = SecurityAccess.extractionLimit(grid, playerSource, key, amount);
        if (limit <= 0) return 0;
        if (delegate instanceof GridBoundStorage network && network.ae2security$grid() != grid) return 0;
        var terminal = SecurityAccess.craftingTerminal(grid);
        var data = terminal != null && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                ? SecuritySavedData.get(serverPlayer.server) : null;
        long allowance = data == null ? 0 : data.craftedAmount(terminal, player.getUUID(), key);
        long extracted = delegate.extract(key, limit, mode, playerSource);
        // SupplierStorage may hide NetworkStorage; only spend what the delegate has not already charged.
        long spent = data == null ? 0 : Math.max(0, allowance - data.craftedAmount(terminal, player.getUUID(), key));
        SecurityAccess.extracted(grid, playerSource, key, Math.max(0, extracted - spent), mode);
        return extracted;
    }
    @Override public void getAvailableStacks(KeyCounter out) {
        if (allowed(Permission.VIEW)) delegate.getAvailableStacks(out);
    }
    @Override public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return allowed(Permission.INSERT) && delegate.isPreferredStorageFor(key, source);
    }
    @Override public Component getDescription() { return delegate.getDescription(); }
}
