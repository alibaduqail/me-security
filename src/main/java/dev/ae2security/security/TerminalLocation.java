package dev.ae2security.security;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Where a Security Terminal part sits: dimension id, cable-bus position and the side it is attached to. */
public record TerminalLocation(String dimension, BlockPos pos, Direction side) {
    public TerminalLocation {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(side);
        pos = pos.immutable();
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("dimension", dimension);
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putString("side", side.getSerializedName());
        return tag;
    }

    /** Null for an incomplete or unreadable tag: an unknown location, never an error. */
    public static TerminalLocation read(CompoundTag tag) {
        if (!tag.contains("dimension", Tag.TAG_STRING) || !tag.contains("side", Tag.TAG_STRING)
                || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT) || !tag.contains("z", Tag.TAG_INT)) {
            return null;
        }
        var dimension = tag.getString("dimension");
        var side = Direction.byName(tag.getString("side"));
        if (dimension.isEmpty() || side == null) return null;
        return new TerminalLocation(dimension, new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), side);
    }

    @Override
    public String toString() {
        return dimension + " " + pos.toShortString() + " " + side.getSerializedName();
    }
}
