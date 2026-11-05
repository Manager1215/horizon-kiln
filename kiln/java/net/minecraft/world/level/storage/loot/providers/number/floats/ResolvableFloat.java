package net.minecraft.world.level.storage.loot.providers.number.floats;

import net.minecraft.world.level.storage.loot.LootContext;

/** Kiln's stand-in for {@code ResolvableFloat}: a number worked out in a loot context (a fuel's speed). */
public interface ResolvableFloat {
    float get(LootContext context, float fallback);

    /** Kiln's own: always {@code value}. */
    static ResolvableFloat kiln$exactly(float value) {
        return (context, fallback) -> value;
    }
}
