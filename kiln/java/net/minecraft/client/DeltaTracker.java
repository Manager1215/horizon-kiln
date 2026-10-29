package net.minecraft.client;

/** Kiln's stand-in for {@code DeltaTracker}: Kiln draws once a tick, so there is no partial tick. */
public interface DeltaTracker {
    DeltaTracker ZERO = new DeltaTracker() {};
}
