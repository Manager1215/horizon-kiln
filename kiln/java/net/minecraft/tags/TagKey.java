package net.minecraft.tags;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code TagKey}: a tag's registry and id, as vanilla's. */
public record TagKey<T>(ResourceKey<? extends Registry<T>> registry, Identifier location) {
    public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, Identifier location) {
        return new TagKey<>(registry, location);
    }

    @Override
    public String toString() {
        return "TagKey[" + (registry == null ? "?" : registry.identifier()) + " / " + location + "]";
    }
}
