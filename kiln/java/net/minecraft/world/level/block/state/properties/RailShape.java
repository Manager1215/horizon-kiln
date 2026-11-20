package net.minecraft.world.level.block.state.properties;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code RailShape}: a rail's shape, by the game's serialized names (a block state's text). */
public enum RailShape implements StringRepresentable {
    NORTH_SOUTH, EAST_WEST, ASCENDING_EAST, ASCENDING_WEST, ASCENDING_NORTH, ASCENDING_SOUTH, SOUTH_EAST, SOUTH_WEST, NORTH_WEST, NORTH_EAST;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return getSerializedName();
    }

    /** Whether the rail goes up a block. */
    public boolean isSlope() {
        return this == ASCENDING_EAST || this == ASCENDING_WEST || this == ASCENDING_NORTH || this == ASCENDING_SOUTH;
    }
}
