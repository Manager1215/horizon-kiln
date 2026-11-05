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

    /** One of the six, at random. */
    public static Direction getRandom(net.minecraft.util.RandomSource random) {
        return values()[random.nextInt(6)];
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

    /** Along which axis it points. */
    public Axis getAxis() {
        return x != 0 ? Axis.X : y != 0 ? Axis.Y : Axis.Z;
    }

    /** Whether it points the axis' positive way. */
    public AxisDirection getAxisDirection() {
        return x + y + z > 0 ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
    }

    /** Its number among the horizontal ones: south, west, north, east are 0 to 3; -1 for up and down. */
    public int get2DDataValue() {
        return switch (this) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> -1;
        };
    }

    public static Direction from2DDataValue(int value) {
        return new Direction[] {SOUTH, WEST, NORTH, EAST}[Math.floorMod(value, 4)];
    }

    /** Its yaw in degrees: south 0, west 90, north 180, east 270. */
    public float toYRot() {
        return (get2DDataValue() & 3) * 90;
    }

    /** Turned a quarter clockwise seen from above (horizontal ones only). */
    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        };
    }

    public Direction getCounterClockWise() {
        return getClockWise().getOpposite();
    }

    public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Direction> STREAM_CODEC =
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(Direction::from3DDataValue,
                    Direction::get3DDataValue);

    /** Kiln's stand-in for {@code Direction.Axis}. */
    public enum Axis implements java.util.function.Predicate<Direction>, StringRepresentable {
        X("x"),
        Y("y"),
        Z("z");

        private final String name;

        Axis(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public boolean isVertical() {
            return this == Y;
        }

        public boolean isHorizontal() {
            return this != Y;
        }

        @Override
        public boolean test(Direction direction) {
            return direction != null && direction.getAxis() == this;
        }

        public int choose(int x, int y, int z) {
            return this == X ? x : this == Y ? y : z;
        }

        public double choose(double x, double y, double z) {
            return this == X ? x : this == Y ? y : z;
        }
    }

    /** Kiln's stand-in for {@code Direction.AxisDirection}. */
    public enum AxisDirection {
        POSITIVE(1),
        NEGATIVE(-1);

        private final int step;

        AxisDirection(int step) {
            this.step = step;
        }

        public int getStep() {
            return step;
        }

        public AxisDirection opposite() {
            return this == POSITIVE ? NEGATIVE : POSITIVE;
        }
    }

    /** Kiln's stand-in for {@code Direction.Plane}. */
    public enum Plane implements java.util.function.Predicate<Direction>, Iterable<Direction> {
        HORIZONTAL(NORTH, EAST, SOUTH, WEST),
        VERTICAL(UP, DOWN);

        private final java.util.List<Direction> faces;

        Plane(Direction... faces) {
            this.faces = java.util.List.of(faces);
        }

        @Override
        public boolean test(Direction direction) {
            return direction != null && faces.contains(direction);
        }

        @Override
        public java.util.Iterator<Direction> iterator() {
            return faces.iterator();
        }

        public java.util.stream.Stream<Direction> stream() {
            return faces.stream();
        }
    }

    /** The direction called {@code name} ({@code north}), or null. */
    /** Its number as the game's data writes it: down, up, north, south, west, east are 0 to 5. */
    public int get3DDataValue() {
        return ordinal();
    }

    public static Direction from3DDataValue(int value) {
        return values()[Math.abs(value % 6)];
    }

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
