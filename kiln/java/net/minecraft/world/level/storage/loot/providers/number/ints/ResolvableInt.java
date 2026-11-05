package net.minecraft.world.level.storage.loot.providers.number.ints;

import net.minecraft.world.level.storage.loot.LootContext;

/** Kiln's stand-in for {@code ResolvableInt}: a whole number worked out in a loot context (a fuel's burn time). */
public interface ResolvableInt {
    int get(LootContext context, int fallback);

    /** Kiln's own: always {@code value}. */
    static ResolvableInt kiln$exactly(int value) {
        return (context, fallback) -> value;
    }
}
