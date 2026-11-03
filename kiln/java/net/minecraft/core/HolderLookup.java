package net.minecraft.core;

/** Kiln's stand-in for {@code HolderLookup}: what code that reads the game's registries is handed. Kiln has none to read. */
public interface HolderLookup<T> {
    interface Provider {}
}
