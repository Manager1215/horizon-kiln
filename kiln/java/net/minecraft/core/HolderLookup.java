package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Kiln's stand-in for {@code HolderLookup}: a registry's entries and tags, for code that reads them. Kiln's
 * registries are its own (what the mods register and the game's entries Kiln names, {@code BuiltInRegistries}): a
 * {@link Provider} finds them by key.
 */
public interface HolderLookup<T> extends HolderGetter<T> {
    default Stream<Holder.Reference<T>> listElements() {
        return Stream.empty();
    }

    default Stream<ResourceKey<T>> listElementIds() {
        return listElements().map(Holder.Reference::key);
    }

    default Stream<HolderSet.Named<T>> listTags() {
        return Stream.empty();
    }

    default Stream<net.minecraft.tags.TagKey<T>> listTagIds() {
        return listTags().map(HolderSet.Named::key);
    }

    interface RegistryLookup<T> extends HolderLookup<T>, HolderOwner<T> {
        ResourceKey<? extends Registry<? extends T>> key();

        default Lifecycle registryLifecycle() {
            return Lifecycle.stable();
        }

        /** Kiln knows no experimental features: every entry is on. */
        default RegistryLookup<T> filterFeatures(FeatureFlagSet flags) {
            return this;
        }
    }

    /** Kiln's registries by key ({@code kiln.Registries}); one made of a world's registries isn't Kiln's yet. */
    interface Provider extends HolderGetter.Provider {
        @Override
        @SuppressWarnings("unchecked")
        default <T> Optional<? extends RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
            return Optional.ofNullable((Registry<T>) kiln.Registries.of((ResourceKey<? extends Registry<Object>>) (Object) key));
        }

        @Override
        default <T> RegistryLookup<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> key) {
            return this.<T>lookup(key).orElseThrow(() -> new IllegalStateException("Registry " + key + " not found"));
        }

        default <V> RegistryOps<V> createSerializationContext(DynamicOps<V> ops) {
            return RegistryOps.create(ops, this);
        }
    }
}
