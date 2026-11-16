package net.minecraft.world.level.storage.loot.providers.number.ints;

import net.minecraft.world.level.storage.loot.LootContext;

/** Kiln's stand-in for {@code ResolvableInt}: a whole number worked out in a loot context (a fuel's burn time). */
public interface ResolvableInt {
    int get(LootContext context, int fallback);

    /** The number {@code stack}'s component of {@code type} gives through {@code getter}, or {@code fallback}. */
    static <T> int getFromItem(net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.component.DataComponentType<T> type, java.util.function.Function<T, ResolvableInt> getter,
            LootContext context, int fallback) {
        T component = stack.get(type);
        ResolvableInt value = component == null ? null : getter.apply(component);
        return value == null ? fallback : value.get(context, fallback);
    }

    /** Kiln's own: always {@code value}. */
    static ResolvableInt kiln$exactly(int value) {
        return (context, fallback) -> value;
    }

    /** A number given in the data itself (a compostable item's layers). */
    record Constant(int value) implements ResolvableInt {
        @Override
        public int get(LootContext context, int fallback) {
            return value;
        }
    }

    /** One of the game's {@code context_int_provider} files, worked out from its resources (D-159). */
    record Reference(net.minecraft.resources.ResourceKey<ContextIntProvider> key) implements ResolvableInt {
        @Override
        public int get(LootContext context, int fallback) {
            return kiln.ClientComponents.resolveInt(key.identifier(), context, fallback);
        }
    }
}
