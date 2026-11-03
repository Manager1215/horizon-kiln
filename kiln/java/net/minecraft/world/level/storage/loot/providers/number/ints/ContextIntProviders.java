package net.minecraft.world.level.storage.loot.providers.number.ints;

import net.minecraft.core.Holder;

/** Kiln's stand-in for {@code ContextIntProviders}: a constant, or a uniform range. */
public class ContextIntProviders {
    private record Range(int kiln$min, int kiln$max) implements ContextIntProvider {}

    public static Holder<ContextIntProvider> exactly(int value) {
        return Holder.direct(new Range(value, value));
    }

    public static Holder<ContextIntProvider> between(int min, int max) {
        return Holder.direct(new Range(min, max));
    }
}
