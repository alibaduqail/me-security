package dev.ae2security.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable authorization state; callers must authenticate the owner before editing. */
public record SecurityPolicy(UUID owner, Map<UUID, Integer> trusted, long revision) {
    public SecurityPolicy {
        Objects.requireNonNull(owner);
        var normalized = new HashMap<UUID, Integer>();
        trusted.forEach((id, mask) -> {
            Objects.requireNonNull(id);
            if (!owner.equals(id)) normalized.put(id, Permission.normalize(mask));
        });
        trusted = Map.copyOf(normalized);
    }

    public static SecurityPolicy create(UUID owner) { return new SecurityPolicy(owner, Map.of(), 0); }
    public boolean isOwner(UUID player) { return owner.equals(player); }
    public int permissions(UUID player) { return isOwner(player) ? Permission.ALL : trusted.getOrDefault(player, 0); }
    public boolean allows(UUID player, Permission permission) { return permission.in(permissions(player)); }

    public SecurityPolicy trust(UUID actor, UUID player, int mask) {
        requireOwner(actor);
        if (owner.equals(player)) throw new IllegalArgumentException("The owner always has all permissions");
        var entries = new HashMap<>(trusted);
        entries.put(player, Permission.normalize(mask));
        return new SecurityPolicy(owner, entries, revision + 1);
    }

    public SecurityPolicy remove(UUID actor, UUID player) {
        requireOwner(actor);
        var entries = new HashMap<>(trusted);
        entries.remove(player);
        return new SecurityPolicy(owner, entries, revision + 1);
    }

    public SecurityPolicy transfer(UUID actor, UUID newOwner) {
        requireOwner(actor);
        if (owner.equals(newOwner)) throw new IllegalArgumentException("Already the owner");
        var entries = new HashMap<>(trusted);
        entries.remove(newOwner);
        entries.remove(owner);
        return new SecurityPolicy(newOwner, entries, revision + 1);
    }

    private void requireOwner(UUID actor) {
        if (!isOwner(actor)) throw new SecurityException("Only the owner may edit access");
    }
}
