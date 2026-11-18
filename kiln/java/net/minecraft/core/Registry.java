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
public interface Registry<T> extends Iterable<T>, HolderLookup.RegistryLookup<T> {
    ResourceKey<? extends Registry<T>> key();

    Identifier getKey(T value);

    Optional<ResourceKey<T>> getResourceKey(T value);

    T getValue(ResourceKey<T> key);

    T getValue(Identifier id);

    Optional<Holder.Reference<T>> get(Identifier id);

    Set<Identifier> keySet();

    boolean containsKey(Identifier id);

    boolean containsKey(ResourceKey<T> key);

    /** Its entries by id, as the game's data writes them. */
    default com.mojang.serialization.Codec<T> byNameCodec() {
        return Identifier.CODEC.xmap(this::getValue, this::getKey);
    }

    default com.mojang.serialization.Codec<Holder<T>> holderByNameCodec() {
        return Identifier.CODEC.xmap(id -> get(id).<Holder<T>>map(h -> h).orElseThrow(),
                h -> h.unwrapKey().orElseThrow().identifier());
    }

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
    /** The entry {@code value} is, by its key, as a holder; a value this registry hasn't is a direct holder. */
    default Holder<T> wrapAsHolder(T value) {
        Identifier id = getKey(value);
        return id == null ? Holder.direct(value) : get(id).<Holder<T>>map(h -> h).orElseGet(() -> Holder.direct(value));
    }

    /** What the registry holds under {@code id}, if anything: never a defaulted registry's default for an id it hasn't. */
    default java.util.Optional<T> getOptional(Identifier id) {
        return containsKey(id) ? java.util.Optional.ofNullable(getValue(id)) : java.util.Optional.empty();
    }

    @Override
    default Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
        return get(key.identifier());
    }

    @Override
    default Stream<Holder.Reference<T>> listElements() {
        return java.util.List.copyOf(keySet()).stream().map(this::get).flatMap(Optional::stream);
    }

    default Optional<Holder.Reference<T>> getAny() {
        return listElements().findFirst();
    }

    /**
     * A tag of the game's (from its resources, {@code kiln.GameItems}): its entries Kiln has. An item tag's are all
     * the game's items in it, as Kiln makes any game item it is asked for.
     */
    @Override
    @SuppressWarnings("unchecked")
    default Optional<HolderSet.Named<T>> get(net.minecraft.tags.TagKey<T> tag) {
        boolean items = net.minecraft.core.registries.Registries.ITEM.equals(key());
        return Optional.of(new HolderSet.Named<>(tag, () -> {
            java.util.List<Holder<T>> holders = new java.util.ArrayList<>();
            for (String id : kiln.GameItems.tagIds(key().identifier().getPath(), tag.location())) {
                Identifier name = Identifier.tryParse(id);
                Optional<Holder.Reference<T>> found = name == null ? Optional.empty() : get(name);
                if (found.isPresent()) {
                    holders.add(found.get());
                } else if (items && name != null) {
                    holders.add((Holder<T>) (Holder<?>) net.minecraft.world.item.Item.kiln$byId(id).builtInRegistryHolder());
                }
            }
            return holders;
        }));
    }

    default Iterable<Holder<T>> getTagOrEmpty(net.minecraft.tags.TagKey<T> tag) {
        return get(tag).<Iterable<Holder<T>>>map(set -> set).orElse(java.util.List.of());
    }

    /** The game's tags of this registry, from its resources (D-159); each one's entries are read when first asked. */
    default Stream<HolderSet.Named<T>> getTags() {
        return kiln.GameTags.names(key().identifier().getPath()).stream()
                .map(name -> get(net.minecraft.tags.TagKey.create(key(), name)).orElseThrow());
    }

    @Override
    default Stream<HolderSet.Named<T>> listTags() {
        return getTags();
    }

    /** Its entries by key, in the order they were registered. */
    default Set<java.util.Map.Entry<ResourceKey<T>, T>> entrySet() {
        Set<java.util.Map.Entry<ResourceKey<T>, T>> entries = new java.util.LinkedHashSet<>();
        for (Identifier id : java.util.List.copyOf(keySet())) {
            entries.add(java.util.Map.entry(ResourceKey.create(key(), id), getValue(id)));
        }
        return entries;
    }

    default Set<ResourceKey<T>> registryKeySet() {
        Set<ResourceKey<T>> keys = new java.util.LinkedHashSet<>();
        for (Identifier id : java.util.List.copyOf(keySet())) {
            keys.add(ResourceKey.create(key(), id));
        }
        return keys;
    }
    /** How many entries it has (vanilla's {@code IdMap.size}). */
    default int size() {
        int n = 0;
        for (T ignored : this) {
            n++;
        }
        return n;
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    default int getId(T p0) { kiln.Missing.called("net.minecraft.core.Registry.getId"); return 0; }
    default java.util.Optional<net.minecraft.core.RegistrationInfo> registrationInfo(net.minecraft.resources.ResourceKey<T> p0) { kiln.Missing.called("net.minecraft.core.Registry.registrationInfo"); return java.util.Optional.empty(); }
    default java.util.Optional<T> getOptional(net.minecraft.resources.ResourceKey<T> p0) { kiln.Missing.called("net.minecraft.core.Registry.getOptional"); return java.util.Optional.empty(); }
    default T getValueOrThrow(net.minecraft.resources.ResourceKey<T> p0) { kiln.Missing.called("net.minecraft.core.Registry.getValueOrThrow"); return null; }
    default java.util.Optional<net.minecraft.core.Holder.Reference<T>> getRandom(net.minecraft.util.RandomSource p0) { kiln.Missing.called("net.minecraft.core.Registry.getRandom"); return java.util.Optional.empty(); }
    default java.util.Optional<net.minecraft.core.Holder.Reference<T>> get(int p0) { kiln.Missing.called("net.minecraft.core.Registry.get"); return java.util.Optional.empty(); }
    default net.minecraft.core.IdMap<net.minecraft.core.Holder<T>> asHolderIdMap() { kiln.Missing.called("net.minecraft.core.Registry.asHolderIdMap"); return null; }
    default net.minecraft.core.Registry.PendingTags<T> prepareTagReload(net.minecraft.tags.TagLoader.LoadResult<T> p0) { kiln.Missing.called("net.minecraft.core.Registry.prepareTagReload"); return null; }
    public static interface PendingTags<T> {
        net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key();
        net.minecraft.core.HolderLookup.RegistryLookup<T> lookup();
        void apply();
        int size();
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
