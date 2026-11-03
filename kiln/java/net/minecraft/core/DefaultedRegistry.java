package net.minecraft.core;

import net.minecraft.resources.Identifier;

/** Kiln's stand-in for {@code DefaultedRegistry}: a registry that answers its default entry for an unknown id. */
public interface DefaultedRegistry<T> extends Registry<T> {
    Identifier getDefaultKey();
}
