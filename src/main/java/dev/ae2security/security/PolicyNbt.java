package dev.ae2security.security;

import java.util.HashMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class PolicyNbt {
    private PolicyNbt() {}

    public static CompoundTag write(SecurityPolicy policy) {
        var tag = new CompoundTag();
        tag.putUUID("owner", policy.owner());
        tag.putLong("revision", policy.revision());
        var entries = new ListTag();
        policy.trusted().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            var row = new CompoundTag();
            row.putUUID("player", entry.getKey());
            row.putInt("permissions", entry.getValue());
            entries.add(row);
        });
        tag.put("trusted", entries);
        return tag;
    }

    public static SecurityPolicy read(CompoundTag tag) {
        if (!tag.hasUUID("owner")) throw new IllegalArgumentException("Security policy has no owner");
        var entries = new HashMap<UUID, Integer>();
        for (var element : tag.getList("trusted", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            if (!row.hasUUID("player")) throw new IllegalArgumentException("Trusted entry has no UUID");
            entries.put(row.getUUID("player"), row.getInt("permissions"));
        }
        return new SecurityPolicy(tag.getUUID("owner"), entries, tag.getLong("revision"));
    }
}
