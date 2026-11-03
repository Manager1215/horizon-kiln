package net.minecraft.core;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code DefaultedMappedRegistry}: an unknown id is its default entry (for items, air). */
public class DefaultedMappedRegistry<T> extends MappedRegistry<T> implements DefaultedRegistry<T> {
    private final Identifier defaultKey;

    public DefaultedMappedRegistry(String defaultKey, ResourceKey<? extends Registry<T>> key) {
        super(key);
        this.defaultKey = Identifier.parse(defaultKey);
    }

    @Override
    public Identifier getDefaultKey() {
        return defaultKey;
    }

    @Override
    public T getValue(Identifier id) {
        T value = super.getValue(id);
        return value != null ? value : super.getValue(defaultKey);
    }
}
