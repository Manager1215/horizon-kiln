package net.minecraft.core;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.registries.SingleRegistryBootstrap;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;

/**
 * Kiln's stand-in for {@code RegistrySetBuilder}: registries built from their bootstraps, on top of others ({@link
 * #build}'s). Its registries' entries are only what the bootstraps register (a recipe viewer's recipes): the others
 * are looked up in the provider it is built on.
 */
public class RegistrySetBuilder {
    private final List<Entry<?>> entries = new ArrayList<>();

    public RegistrySetBuilder() {}

    public <T> RegistrySetBuilder add(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle,
            SingleRegistryBootstrap<T> bootstrap) {
        entries.add(new Entry<>(key, bootstrap));
        return this;
    }

    public <T> RegistrySetBuilder add(ResourceKey<? extends Registry<T>> key, SingleRegistryBootstrap<T> bootstrap) {
        return add(key, Lifecycle.stable(), bootstrap);
    }

    public HolderLookup.Provider build(HolderLookup.Provider base) {
        Map<ResourceKey<?>, HolderLookup.RegistryLookup<?>> built = new LinkedHashMap<>();
        for (Entry<?> entry : entries) {
            built.put(entry.key(), entry.build(base));
        }
        return new HolderLookup.Provider() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(
                    ResourceKey<? extends Registry<? extends T>> key) {
                HolderLookup.RegistryLookup<?> found = built.get(key);
                return found != null ? Optional.of((HolderLookup.RegistryLookup<T>) found)
                        : base == null ? Optional.empty() : base.lookup(key);
            }
        };
    }

    private record Entry<T>(ResourceKey<? extends Registry<T>> key, SingleRegistryBootstrap<T> bootstrap) {
        HolderLookup.RegistryLookup<T> build(HolderLookup.Provider base) {
            Map<ResourceKey<T>, Holder.Reference<T>> registered = new LinkedHashMap<>();
            // A bootstrap that names what Kiln lacks (the game's loot predicates) leaves its registry empty.
            try {
                bootstrap.run(new BootstrapContext<T>() {
                    @Override
                    public Holder.Reference<T> register(ResourceKey<T> id, T value, Lifecycle lifecycle) {
                        Holder.Reference<T> holder = Holder.Reference.kiln$of(id, value);
                        registered.put(id, holder);
                        return holder;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> registry) {
                        return base == null ? null : (HolderGetter<S>) base.lookupOrThrow(registry);
                    }

                    @Override
                    public <S> Stream<Holder.Reference<S>> listContextElements(
                            ResourceKey<? extends Registry<? extends S>> registry) {
                        return base == null ? Stream.empty() : base.<S>lookupOrThrow(registry).listElements();
                    }
                });
            } catch (LinkageError e) {
                kiln.Native.log("a registry's bootstrap names what Kiln hasn't: " + key.identifier() + ": " + e);
            }
            ResourceKey<? extends Registry<T>> registryKey = key;
            return new HolderLookup.RegistryLookup<T>() {
                @Override
                public ResourceKey<? extends Registry<? extends T>> key() {
                    return registryKey;
                }

                @Override
                public Stream<Holder.Reference<T>> listElements() {
                    return registered.values().stream();
                }

                @Override
                public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
                    return Optional.ofNullable(registered.get(id));
                }
            };
        }
    }
}
