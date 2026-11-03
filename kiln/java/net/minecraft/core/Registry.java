package net.minecraft.core;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Kiln's stand-in for {@code Registry}: entries by id. Kiln's registries hold what the Java mods register (and the
 * few vanilla entries Kiln names), not the game's: Horizon has those.
 */
public interface Registry<T> extends Iterable<T> {
    ResourceKey<? extends Registry<T>> key();

    Identifier getKey(T value);

    Optional<ResourceKey<T>> getResourceKey(T value);

    T getValue(ResourceKey<T> key);

    T getValue(Identifier id);

    Optional<Holder.Reference<T>> get(Identifier id);

    Set<Identifier> keySet();

    boolean containsKey(Identifier id);

    boolean containsKey(ResourceKey<T> key);

    default Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @SuppressWarnings("unchecked")
    static <T> T register(Registry<? super T> registry, String name, T value) {
        return register((Registry<Object>) registry, Identifier.parse(name), value);
    }

    static <V, T extends V> T register(Registry<V> registry, Identifier id, T value) {
        return register(registry, ResourceKey.create(registry.key(), id), value);
    }

    static <V, T extends V> T register(Registry<V> registry, ResourceKey<V> key, T value) {
        ((MappedRegistry<V>) registry).kiln$add(key, value);
        return value;
    }

    @SuppressWarnings("unchecked")
    static <R, T extends R> Holder.Reference<T> registerForHolder(Registry<R> registry, ResourceKey<R> key, T value) {
        return (Holder.Reference<T>) ((MappedRegistry<R>) registry).kiln$add(key, value);
    }

    static <R, T extends R> Holder.Reference<T> registerForHolder(Registry<R> registry, Identifier id, T value) {
        return registerForHolder(registry, ResourceKey.create(registry.key(), id), value);
    }
}
