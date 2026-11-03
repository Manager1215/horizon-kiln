package net.minecraft.core;

import net.minecraft.util.StringRepresentable;

/** Kiln's stand-in for {@code Direction}, in vanilla's order. */
public enum Direction implements StringRepresentable {
    DOWN(0, -1, 0, "down"),
    UP(0, 1, 0, "up"),
    NORTH(0, 0, -1, "north"),
    SOUTH(0, 0, 1, "south"),
    WEST(-1, 0, 0, "west"),
    EAST(1, 0, 0, "east");

    private final int x;
    private final int y;
    private final int z;
    private final String name;

    Direction(int x, int y, int z, String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
    }

    public int getStepX() {
        return x;
    }

    public int getStepY() {
        return y;
    }

    public int getStepZ() {
        return z;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Direction getOpposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /** The direction called {@code name} ({@code north}), or null. */
    public static Direction byName(String name) {
        for (Direction d : values()) {
            if (d.name.equals(name)) {
                return d;
            }
        }
        return null;
    }

    /** Kiln's own: the direction of a step ({@code 0 1 0} is up), or null. */
    public static Direction kiln$of(int x, int y, int z) {
        for (Direction d : values()) {
            if (d.x == x && d.y == y && d.z == z) {
                return d;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name;
    }
}
