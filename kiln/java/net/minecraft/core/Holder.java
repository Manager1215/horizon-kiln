package net.minecraft.core;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code Holder}: a registry entry, by its key ({@link Reference}) or by itself ({@link Direct}). */
public interface Holder<T> {
    T value();

    Optional<ResourceKey<T>> unwrapKey();

    default boolean is(Identifier id) {
        return unwrapKey().map(k -> k.identifier().equals(id)).orElse(false);
    }

    default boolean is(ResourceKey<T> key) {
        return unwrapKey().map(k -> k == key).orElse(false);
    }

    default boolean is(Predicate<ResourceKey<T>> predicate) {
        return unwrapKey().map(predicate::test).orElse(false);
    }

    default boolean is(Holder<T> other) {
        return other == this || unwrapKey().isPresent() && unwrapKey().equals(other.unwrapKey());
    }

    static <T> Holder<T> direct(T value) {
        return new Direct<>(value);
    }

    record Direct<T>(T value) implements Holder<T> {
        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.empty();
        }
    }

    final class Reference<T> implements Holder<T> {
        private final ResourceKey<T> key;
        private final T value;

        private Reference(ResourceKey<T> key, T value) {
            this.key = key;
            this.value = value;
        }

        /** Kiln's own: the entry {@code key} names. */
        public static <T> Reference<T> kiln$of(ResourceKey<T> key, T value) {
            return new Reference<>(key, value);
        }

        public ResourceKey<T> key() {
            return key;
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.of(key);
        }

        @Override
        public String toString() {
            return "Reference{" + key + "=" + value + "}";
        }
    }
}
