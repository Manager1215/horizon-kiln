package net.minecraft.world.level.block.state.properties;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code DoubleBlockHalf}: the half of a two-block-tall block (a door, a tall plant). */
public enum DoubleBlockHalf implements StringRepresentable {
    UPPER(Direction.DOWN),
    LOWER(Direction.UP);

    private final Direction directionToOther;

    DoubleBlockHalf(Direction directionToOther) {
        this.directionToOther = directionToOther;
    }

    /** Where the block's other half is. */
    public Direction getDirectionToOther() {
        return directionToOther;
    }

    public DoubleBlockHalf getOtherHalf() {
        return this == UPPER ? LOWER : UPPER;
    }

    @Override
    public String toString() {
        return getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return this == UPPER ? "upper" : "lower";
    }
}
