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
public class MappedRegistry<T> implements WritableRegistry<T> {
    private final ResourceKey<? extends Registry<T>> key;
    private final Map<Identifier, Holder.Reference<T>> byId = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<T, Holder.Reference<T>> byValue = Collections.synchronizedMap(new IdentityHashMap<>());

    public MappedRegistry(ResourceKey<? extends Registry<T>> key) {
        this.key = key;
    }

    /** The game's: a registry of {@code key}; Kiln's keep no lifecycle, nor intrusive holders. */
    public MappedRegistry(ResourceKey<? extends Registry<T>> key, com.mojang.serialization.Lifecycle lifecycle) {
        this(key);
    }

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, com.mojang.serialization.Lifecycle lifecycle,
            boolean hasIntrusiveHolders) {
        this(key);
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
    /** Whether it holds nothing yet. */
    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /** The tags of its entries, as the game binds them on a reload: Kiln reads the game's tags itself. */
    public void bindTags(java.util.Map<net.minecraft.tags.TagKey<T>, java.util.List<Holder<T>>> tags) {
        kiln.Missing.called("net.minecraft.core.MappedRegistry.bindTags");
    }

    public HolderGetter<T> createRegistrationLookup() {
        kiln.Missing.called("net.minecraft.core.MappedRegistry.createRegistrationLookup");
        return null;
    }

    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public it.unimi.dsi.fastutil.objects.Reference2IntMap<T> toId;
    public java.util.Map<net.minecraft.resources.Identifier, net.minecraft.core.Holder.Reference<T>> byLocation;
    public java.util.Map<net.minecraft.resources.ResourceKey<T>, net.minecraft.core.Holder.Reference<T>> byKey;
    public java.util.Map<net.minecraft.resources.ResourceKey<T>, net.minecraft.core.RegistrationInfo> registrationInfos;
    public boolean frozen;
    public net.minecraft.core.Holder.Reference<T> register(net.minecraft.resources.ResourceKey<T> p0, T p1, net.minecraft.core.RegistrationInfo p2) { kiln.Missing.called("net.minecraft.core.MappedRegistry.register"); return null; }
    public net.minecraft.core.Registry<T> freeze() { kiln.Missing.called("net.minecraft.core.MappedRegistry.freeze"); return null; }
    public static interface TagSet<T> {
        boolean isBound();
        java.util.Optional<net.minecraft.core.HolderSet.Named<T>> get(net.minecraft.tags.TagKey<T> p0);
        void forEach(java.util.function.BiConsumer<? super net.minecraft.tags.TagKey<T>, ? super net.minecraft.core.HolderSet.Named<T>> p0);
        java.util.stream.Stream<net.minecraft.core.HolderSet.Named<T>> getTags();
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
