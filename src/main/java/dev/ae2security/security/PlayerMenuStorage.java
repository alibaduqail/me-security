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
        return allowed(Permission.EXTRACT) ? delegate.extract(key, amount, mode, source) : 0;
    }
    @Override public void getAvailableStacks(KeyCounter out) {
        if (allowed(Permission.VIEW)) delegate.getAvailableStacks(out);
    }
    @Override public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return allowed(Permission.INSERT) && delegate.isPreferredStorageFor(key, source);
    }
    @Override public Component getDescription() { return delegate.getDescription(); }
}
