package net.minecraft.core;

import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code TypedInstance}: something of a registered type (a block state is of its block). */
public interface TypedInstance<T> {
    Holder<T> typeHolder();

    default boolean is(T type) {
        return typeHolder().value() == type;
    }

    default boolean is(Holder<T> type) {
        return typeHolder().is(type);
    }

    default boolean is(ResourceKey<T> key) {
        return typeHolder().is(key);
    }
}
