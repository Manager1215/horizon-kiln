package net.minecraft.world.ticks;

/** Kiln's stand-in for {@code TickPriority}: which scheduled ticks of a game tick run first. */
public enum TickPriority {
    EXTREMELY_HIGH(-3),
    VERY_HIGH(-2),
    HIGH(-1),
    NORMAL(0),
    LOW(1),
    VERY_LOW(2),
    EXTREMELY_LOW(3);

    private final int value;

    TickPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
