package net.minecraft.core;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;

/**
 * Kiln's stand-in for {@code RegistryAccess}: a world's registries. Kiln's are global ({@code BuiltInRegistries}:
 * what the mods register and the game's entries Kiln names), so every world's is {@link #KILN}.
 */
public interface RegistryAccess extends HolderLookup.Provider {
    /** Kiln's own: Kiln's registries, as the one access every level hands out. */
    Frozen KILN = new Frozen() {};

    @SuppressWarnings("unchecked")
    default <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
        return Optional.ofNullable((Registry<E>) kiln.Registries.of((ResourceKey<? extends Registry<Object>>) (Object) key));
    }

    @Override
    default <E> Registry<E> lookupOrThrow(ResourceKey<? extends Registry<? extends E>> key) {
        return this.<E>lookup(key).orElseThrow(() -> new IllegalStateException("Missing registry: " + key));
    }

    default Stream<RegistryEntry<?>> registries() {
        return kiln.Registries.all().stream().map(RegistryEntry::kiln$of);
    }

    default Frozen freeze() {
        return KILN;
    }

    record RegistryEntry<T>(ResourceKey<? extends Registry<T>> key, Registry<T> value) {
        static <T> RegistryEntry<T> kiln$of(Registry<T> registry) {
            return new RegistryEntry<>(registry.key(), registry);
        }
    }

    interface Frozen extends RegistryAccess {}
}
