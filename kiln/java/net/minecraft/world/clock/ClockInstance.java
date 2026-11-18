package net.minecraft.world.clock;

/** Kiln's stand-in for {@code ClockInstance}: a clock as it is now. */
public interface ClockInstance {
    long totalTicks();

    float partialTick();

    float rate();

    boolean isPaused();
}
