package net.minecraft.world.level.storage.loot.providers.number.floats;

import net.minecraft.world.level.storage.loot.LootContext;

/** Kiln's stand-in for {@code ResolvableFloat}: a number worked out in a loot context (a fuel's speed). */
public interface ResolvableFloat {
    float get(LootContext context, float fallback);

    /** The number {@code stack}'s component of {@code type} gives through {@code getter}, or {@code fallback}. */
    static <T> float getFromItem(net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.component.DataComponentType<T> type,
            java.util.function.Function<T, ResolvableFloat> getter, LootContext context, float fallback) {
        T component = stack.get(type);
        ResolvableFloat value = component == null ? null : getter.apply(component);
        return value == null ? fallback : value.get(context, fallback);
    }

    /** Kiln's own: always {@code value}. */
    static ResolvableFloat kiln$exactly(float value) {
        return (context, fallback) -> value;
    }
}
