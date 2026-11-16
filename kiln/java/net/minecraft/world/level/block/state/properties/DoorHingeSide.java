package net.minecraft.world.level.block.state.properties;

import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code DoorHingeSide}: the side a door's hinge is on, seen from where it faces. */
public enum DoorHingeSide implements StringRepresentable {
    LEFT,
    RIGHT;

    @Override
    public String toString() {
        return getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return this == LEFT ? "left" : "right";
    }
}
