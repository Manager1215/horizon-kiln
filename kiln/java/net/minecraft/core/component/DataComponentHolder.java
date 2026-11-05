package net.minecraft.core.component;

/** Kiln's stand-in for {@code DataComponentHolder}: something with components (an item stack). */
public interface DataComponentHolder extends DataComponentGetter {
    DataComponentMap getComponents();

    @Override
    default <T> T get(DataComponentType<? extends T> type) {
        return getComponents().get(type);
    }

    default boolean has(DataComponentType<?> type) {
        return getComponents().has(type);
    }
}
