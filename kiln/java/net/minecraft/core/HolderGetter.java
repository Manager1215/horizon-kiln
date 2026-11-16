package net.minecraft.core;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/** Kiln's stand-in for {@code HolderGetter}: where registry entries and tags are looked up. Kiln's registries are global. */
public interface HolderGetter<T> {
    default Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
        return Optional.empty();
    }

    default Holder.Reference<T> getOrThrow(ResourceKey<T> key) {
        return get(key).orElseThrow(() -> new IllegalStateException("Missing element " + key));
    }

    default Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
        return Optional.empty();
    }

    default HolderSet.Named<T> getOrThrow(TagKey<T> tag) {
        return get(tag).orElseThrow(() -> new IllegalStateException("Missing tag " + tag));
    }

    interface Provider {
        <T> Optional<? extends HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> key);

        default <T> HolderGetter<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> key) {
            return this.<T>lookup(key).orElseThrow(() -> new IllegalStateException("Registry " + key + " not found"));
        }
    }
}
