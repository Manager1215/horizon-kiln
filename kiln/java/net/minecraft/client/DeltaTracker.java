package net.minecraft.client;

/** Kiln's stand-in for {@code DeltaTracker}: Kiln draws once a tick, so there is no partial tick. */
public interface DeltaTracker {
    DeltaTracker ZERO = new DefaultValue(0F);
    DeltaTracker ONE = new DefaultValue(1F);

    float getGameTimeDeltaTicks();

    float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame);

    float getRealtimeDeltaTicks();

    /** The same value for each. */
    record DefaultValue(float value) implements DeltaTracker {
        @Override
        public float getGameTimeDeltaTicks() {
            return value;
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame) {
            return value;
        }

        @Override
        public float getRealtimeDeltaTicks() {
            return value;
        }
    }
}
