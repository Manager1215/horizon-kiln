package net.minecraft.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
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

    /**
     * Kiln's own: puts the entries of {@code first} (those it has, in that order) before the rest, the rest in their
     * order. The game registers its own entries before any mod's; Kiln adds some of the game's later (its creative
     * tabs, on joining a world: kiln.GameTabs), and moves them to where the game has them (D-172).
     */
    public void kiln$putFirst(List<Identifier> first) {
        synchronized (byId) {
            Map<Identifier, Holder.Reference<T>> all = new LinkedHashMap<>(byId);
            byId.clear();
            for (Identifier id : first) {
                Holder.Reference<T> holder = all.remove(id);
                if (holder != null) {
                    byId.put(id, holder);
                }
            }
            byId.putAll(all);
        }
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

    /**
     * The ids in the order they were registered. ({@code Set.copyOf}'s order changed from one run to the next, and
     * so did {@code listElements}': JEI's item list follows the creative tabs' order there, D-172.)
     */
    @Override
    public Set<Identifier> keySet() {
        synchronized (byId) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(byId.keySet()));
        }
    }

    /** The entries in the order they were registered, as the game's registry lists them. */
    @Override
    public Stream<Holder.Reference<T>> listElements() {
        synchronized (byId) {
            return List.copyOf(byId.values()).stream();
        }
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
        synchronized (byId) {
            return byId.values().stream().map(Holder.Reference::value).toList().iterator();
        }
    }

    @Override
    public String toString() {
        return "Registry[" + key + "]";
    }
}
