package net.minecraft.world.flag;

/**
 * Kiln's stand-in for {@code FeatureFlags}: the game's sets of experimental features. Kiln knows of none, so every
 * set is the empty one (a mod's menu type requires the vanilla set, D-165).
 */
public final class FeatureFlags {
    public static final FeatureFlagSet VANILLA_SET = FeatureFlagSet.of();
    public static final FeatureFlagSet DEFAULT_FLAGS = FeatureFlagSet.of();

    private FeatureFlags() {}
}
