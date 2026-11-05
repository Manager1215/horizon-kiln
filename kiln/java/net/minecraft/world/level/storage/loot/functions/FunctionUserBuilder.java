package net.minecraft.world.level.storage.loot.functions;

import java.util.function.Function;

/**
 * Kiln's stand-in for {@code FunctionUserBuilder}: a loot builder that takes functions. Kiln drops the stack as the
 * entry names it: the functions are taken and left out.
 */
public interface FunctionUserBuilder<T extends FunctionUserBuilder<T>> {
    default T apply(LootItemFunction.Builder function) {
        return unwrap();
    }

    default <E> T apply(Iterable<E> values, Function<E, LootItemFunction.Builder> function) {
        return unwrap();
    }

    default <E> T apply(E[] values, Function<E, LootItemFunction.Builder> function) {
        return unwrap();
    }

    T unwrap();
}
