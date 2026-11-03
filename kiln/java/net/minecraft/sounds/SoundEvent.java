package net.minecraft.sounds;

import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Kiln's stand-in for {@code SoundEvent}: a sound by its id, which Horizon plays. */
public record SoundEvent(Identifier location, Optional<Float> fixedRange) {
    public static SoundEvent createVariableRangeEvent(Identifier location) {
        return new SoundEvent(location, Optional.empty());
    }

    public static SoundEvent createFixedRangeEvent(Identifier location, float range) {
        return new SoundEvent(location, Optional.of(range));
    }

    public float getRange(float volume) {
        return fixedRange.orElse(volume > 1.0F ? 16.0F * volume : 16.0F);
    }
}
