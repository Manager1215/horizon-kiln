package net.minecraft.world;

/**
 * Kiln's stand-in for {@code TickRateManager}: the client's tick rate. Horizon's client ticks 20 times a second and
 * Kiln's is never frozen or stepped ({@code /tick} freezes a server's); a mod that sets a rate keeps it in Java.
 */
public class TickRateManager {
    public static final float MIN_TICKRATE = 1.0F;
    private float tickrate = 20.0F;
    private boolean frozen;
    private int frozenTicksToRun;

    public TickRateManager() {}

    public void setTickRate(float rate) {
        tickrate = Math.max(rate, MIN_TICKRATE);
    }

    public float tickrate() {
        return tickrate;
    }

    public float millisecondsPerTick() {
        return 1000.0F / tickrate;
    }

    public long nanosecondsPerTick() {
        return (long) (1.0E9 / tickrate);
    }

    public boolean runsNormally() {
        return !frozen || frozenTicksToRun > 0;
    }

    public boolean isSteppingForward() {
        return frozenTicksToRun > 0;
    }

    public void setFrozenTicksToRun(int ticks) {
        frozenTicksToRun = ticks;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /** Whether an entity stands still for the freeze: players never do, as vanilla's. */
    public boolean isEntityFrozen(net.minecraft.world.entity.Entity entity) {
        return !runsNormally() && !(entity instanceof net.minecraft.world.entity.player.Player);
    }
}
