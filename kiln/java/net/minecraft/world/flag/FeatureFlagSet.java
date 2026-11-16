package net.minecraft.world.flag;

/** Kiln's stand-in for {@code FeatureFlagSet}: the game's experimental features turned on. Kiln knows of none. */
public final class FeatureFlagSet {
    private static final FeatureFlagSet EMPTY = new FeatureFlagSet();

    private FeatureFlagSet() {}

    /** No feature: what every game element Kiln names requires. */
    public static FeatureFlagSet of() {
        return EMPTY;
    }
}
