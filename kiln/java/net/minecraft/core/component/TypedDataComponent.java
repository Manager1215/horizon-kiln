package net.minecraft.core.component;

/** Kiln's stand-in for {@code TypedDataComponent}: a component's type and its value. */
public record TypedDataComponent<T>(DataComponentType<T> type, T value) {}
