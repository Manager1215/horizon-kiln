package net.minecraft.world.level.storage.loot.functions;

import java.util.function.Function;

/**
 * Kiln's stand-in for {@code FunctionUserBuilder}: a loot builder that takes functions. An entry's builder keeps them
 * (kiln.Loot writes those it knows, a count); other builders take them and leave them out.
 */
public interface FunctionUserBuilder<T extends FunctionUserBuilder<T>> {
    default T apply(LootItemFunction.Builder function) {
        return unwrap();
    }

    default <E> T apply(Iterable<E> values, Function<E, LootItemFunction.Builder> function) {
        T result = unwrap();
        for (E value : values) {
            result = apply(function.apply(value));
        }
        return result;
    }

    default <E> T apply(E[] values, Function<E, LootItemFunction.Builder> function) {
        return apply(java.util.Arrays.asList(values), function);
    }

    T unwrap();
}
