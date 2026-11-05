package net.minecraft.core.component;

/** Kiln's stand-in for {@code DataComponentGetter}: what reads components. */
public interface DataComponentGetter {
    <T> T get(DataComponentType<? extends T> type);

    default <T> T getOrDefault(DataComponentType<? extends T> type, T fallback) {
        T value = get(type);
        return value != null ? value : fallback;
    }

    @SuppressWarnings("unchecked")
    default <T> TypedDataComponent<T> getTyped(DataComponentType<T> type) {
        T value = get(type);
        return value == null ? null : new TypedDataComponent<>(type, value);
    }
}
