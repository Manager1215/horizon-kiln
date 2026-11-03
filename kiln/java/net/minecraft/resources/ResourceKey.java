package net.minecraft.resources;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;

/**
 * Kiln's stand-in for {@code ResourceKey}: a registry entry's name and its registry's ({@code minecraft:overworld} in
 * {@code minecraft:dimension}). As in vanilla there is one key a name, so keys compare with {@code ==}.
 */
public class ResourceKey<T> {
    private static final ConcurrentHashMap<String, ResourceKey<?>> KEYS = new ConcurrentHashMap<>();
    private static final Identifier ROOT = Identifier.withDefaultNamespace("root");
    private final Identifier registry;
    private final Identifier identifier;

    private ResourceKey(Identifier registry, Identifier identifier) {
        this.registry = registry;
        this.identifier = identifier;
    }

    @SuppressWarnings("unchecked")
    private static <T> ResourceKey<T> of(Identifier registry, Identifier identifier) {
        return (ResourceKey<T>) KEYS.computeIfAbsent(registry + " " + identifier, k -> new ResourceKey<>(registry, identifier));
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registry, Identifier identifier) {
        return of(registry.identifier, identifier);
    }

    public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier identifier) {
        return of(ROOT, identifier);
    }

    public Identifier identifier() {
        return identifier;
    }

    public Identifier registry() {
        return registry;
    }

    public ResourceKey<Registry<T>> registryKey() {
        return of(ROOT, registry);
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> registry) {
        return this.registry.equals(registry.identifier());
    }

    @SuppressWarnings("unchecked")
    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> registry) {
        return isFor(registry) ? Optional.of((ResourceKey<E>) this) : Optional.empty();
    }

    @Override
    public String toString() {
        return "ResourceKey[" + registry + " / " + identifier + "]";
    }
}
