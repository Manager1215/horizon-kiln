package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Kiln's stand-in for {@code StringRepresentable}: a value with the name the game's data writes for it, and the codecs
 * of such values (an enum's: a recipe's book category, D-152), read and written by that name as the game's are.
 */
public interface StringRepresentable {
    String getSerializedName();

    static <E extends Enum<E> & StringRepresentable> EnumCodec<E> fromEnum(Supplier<E[]> values) {
        E[] all = values.get();
        Map<String, E> byName = new HashMap<>();
        for (E e : all) {
            byName.put(e.getSerializedName(), e);
        }
        return new EnumCodec<>(all, byName::get);
    }

    static <T extends StringRepresentable> Codec<T> fromValues(Supplier<T[]> values) {
        T[] all = values.get();
        Map<String, T> byName = new HashMap<>();
        for (T t : all) {
            byName.put(t.getSerializedName(), t);
        }
        return new StringRepresentableCodec<>(all, byName::get, t -> Arrays.asList(all).indexOf(t));
    }

    /** An enum's codec: by name, and its {@link #byName} lookups. */
    class EnumCodec<E extends Enum<E> & StringRepresentable> extends StringRepresentableCodec<E> {
        private final Function<String, E> resolver;

        public EnumCodec(E[] values, Function<String, E> resolver) {
            super(values, resolver, Enum::ordinal);
            this.resolver = resolver;
        }

        public E byName(String name) {
            return name == null ? null : resolver.apply(name);
        }

        public E byName(String name, E fallback) {
            E found = byName(name);
            return found == null ? fallback : found;
        }

        public E byName(String name, Supplier<? extends E> fallback) {
            E found = byName(name);
            return found == null ? fallback.get() : found;
        }
    }

    /** Values read and written by their names; a compressed form's number reads as its index. */
    class StringRepresentableCodec<S extends StringRepresentable> implements Codec<S> {
        private final S[] values;
        private final Function<String, S> resolver;

        public StringRepresentableCodec(S[] values, Function<String, S> resolver, ToIntFunction<S> index) {
            this.values = values;
            this.resolver = resolver;
        }

        @Override
        public <T> DataResult<Pair<S, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<String> name = ops.getStringValue(input);
            if (name.result().isPresent()) {
                S found = resolver.apply(name.result().get());
                return found == null ? DataResult.error(() -> "Unknown element name: " + name.result().get())
                        : DataResult.success(Pair.of(found, ops.empty()));
            }
            return ops.getNumberValue(input).flatMap(n -> {
                int i = n.intValue();
                return i >= 0 && i < values.length ? DataResult.success(Pair.of(values[i], ops.empty()))
                        : DataResult.error(() -> "Unknown element id: " + i);
            });
        }

        @Override
        public <T> DataResult<T> encode(S input, DynamicOps<T> ops, T prefix) {
            return ops.mergeToPrimitive(prefix, ops.createString(input.getSerializedName()));
        }
    }
}
