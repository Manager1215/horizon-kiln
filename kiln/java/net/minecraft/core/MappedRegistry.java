package net.minecraft.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code MappedRegistry}: entries in the order they were registered. */
public class MappedRegistry<T> implements Registry<T> {
    private final ResourceKey<? extends Registry<T>> key;
    private final Map<Identifier, Holder.Reference<T>> byId = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<T, Holder.Reference<T>> byValue = Collections.synchronizedMap(new IdentityHashMap<>());

    public MappedRegistry(ResourceKey<? extends Registry<T>> key) {
        this.key = key;
    }

    /** Kiln's own: {@code Registry.register}. Registering an id again replaces it, as a data pack would. */
    public Holder.Reference<T> kiln$add(ResourceKey<T> key, T value) {
        Holder.Reference<T> holder = Holder.Reference.kiln$of(key, value);
        byId.put(key.identifier(), holder);
        byValue.put(value, holder);
        return holder;
    }

    @Override
    public ResourceKey<? extends Registry<T>> key() {
        return key;
    }

    @Override
    public Identifier getKey(T value) {
        Holder.Reference<T> holder = byValue.get(value);
        return holder == null ? null : holder.key().identifier();
    }

    @Override
    public Optional<ResourceKey<T>> getResourceKey(T value) {
        return Optional.ofNullable(byValue.get(value)).map(Holder.Reference::key);
    }

    @Override
    public T getValue(ResourceKey<T> key) {
        return key == null ? null : getValue(key.identifier());
    }

    @Override
    public T getValue(Identifier id) {
        Holder.Reference<T> holder = id == null ? null : byId.get(id);
        return holder == null ? null : holder.value();
    }

    @Override
    public Optional<Holder.Reference<T>> get(Identifier id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Set<Identifier> keySet() {
        return Set.copyOf(byId.keySet());
    }

    @Override
    public boolean containsKey(Identifier id) {
        return byId.containsKey(id);
    }

    @Override
    public boolean containsKey(ResourceKey<T> key) {
        return byId.containsKey(key.identifier());
    }

    @Override
    public Iterator<T> iterator() {
        return byId.values().stream().map(Holder.Reference::value).toList().iterator();
    }

    @Override
    public String toString() {
        return "Registry[" + key + "]";
    }
}
