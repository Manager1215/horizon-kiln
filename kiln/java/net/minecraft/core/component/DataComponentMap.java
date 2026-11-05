package net.minecraft.core.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Kiln's stand-in for {@code DataComponentMap}: components by type. */
public interface DataComponentMap extends Iterable<TypedDataComponent<?>>, DataComponentGetter {
    DataComponentMap EMPTY = new Builder().build();

    static Builder builder() {
        return new Builder();
    }

    Set<DataComponentType<?>> keySet();

    default boolean has(DataComponentType<?> type) {
        return get(type) != null;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    default Iterator<TypedDataComponent<?>> iterator() {
        List<TypedDataComponent<?>> all = new ArrayList<>();
        for (DataComponentType<?> type : keySet()) {
            all.add(new TypedDataComponent(type, get(type)));
        }
        return all.iterator();
    }

    default Stream<TypedDataComponent<?>> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    default int size() {
        return keySet().size();
    }

    default boolean isEmpty() {
        return size() == 0;
    }

    class Builder {
        private final Map<DataComponentType<?>, Object> values = new IdentityHashMap<>();

        public <T> Builder set(DataComponentType<T> type, T value) {
            if (value == null) {
                values.remove(type);
            } else {
                values.put(type, value);
            }
            return this;
        }

        public Builder addAll(DataComponentMap map) {
            for (TypedDataComponent<?> c : map) {
                values.put(c.type(), c.value());
            }
            return this;
        }

        public DataComponentMap build() {
            Map<DataComponentType<?>, Object> copy = Collections.unmodifiableMap(new IdentityHashMap<>(values));
            return new DataComponentMap() {
                @Override
                @SuppressWarnings("unchecked")
                public <T> T get(DataComponentType<? extends T> type) {
                    return (T) copy.get(type);
                }

                @Override
                public Set<DataComponentType<?>> keySet() {
                    return copy.keySet();
                }
            };
        }
    }
}
