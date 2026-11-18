package net.minecraft.world.clock;

import net.minecraft.core.Holder;

/** Kiln's stand-in for {@code ClockManager}: the world's clocks, each as it is now. */
public interface ClockManager {
    ClockInstance getInstance(Holder<WorldClock> clock);
}
