package dev.ae2security.security;

import java.util.Optional;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.*;
import net.minecraft.world.entity.player.Player;

/** Only created inside the authorized CPU ingredient transaction; never accepted from a packet. */
public final class CraftingActionSource implements IActionSource {
    private final IActionSource delegate;
    private final IGrid grid;
    private CraftingActionSource(IActionSource delegate, IGrid grid) { this.delegate = delegate; this.grid = grid; }
    public static IActionSource approved(IActionSource source, IGrid grid) {
        if (source.player().isPresent() && !SecurityAccess.allow(grid, source.player().get(), Permission.CRAFT, false)) {
            throw new SecurityException("Unapproved crafting request");
        }
        return new CraftingActionSource(source, grid);
    }
    public IGrid grid() { return grid; }
    @Override public Optional<Player> player() { return delegate.player(); }
    @Override public Optional<IActionHost> machine() { return delegate.machine(); }
    @Override public <T> Optional<T> context(Class<T> type) { return delegate.context(type); }
}
