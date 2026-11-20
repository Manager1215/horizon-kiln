package net.minecraft.resources;

/**
 * Kiln's stand-in for {@code RegistryDataLoader}: loads the data-driven registries from data packs. Kiln's registries
 * are one set for every world ({@code RegistryAccess.KILN}), so a load answers them, and the reloadable registries
 * Kiln would load are none (REI's local recipes, D-217).
 */
public class RegistryDataLoader {
    public static final java.util.List<net.minecraft.resources.RegistryDataLoader.RegistryData<?>> WORLD_REGISTRIES = null;
    public static final java.util.List<net.minecraft.resources.RegistryDataLoader.RegistryData<?>> RELOADABLE_REGISTRIES = java.util.List.of();
    public static final java.util.List<net.minecraft.resources.RegistryDataLoader.RegistryData<?>> SYNCHRONIZED_REGISTRIES = null;
    public RegistryDataLoader() {  }
    public static java.util.concurrent.CompletableFuture<net.minecraft.core.RegistryAccess.Frozen> load(net.minecraft.server.packs.resources.ResourceManager p0, java.util.List<net.minecraft.core.HolderLookup.RegistryLookup<?>> p1, java.util.List<net.minecraft.resources.RegistryDataLoader.RegistryData<?>> p2, java.util.concurrent.Executor p3) { return java.util.concurrent.CompletableFuture.completedFuture(net.minecraft.core.RegistryAccess.KILN); }
    public static java.util.concurrent.CompletableFuture<net.minecraft.core.RegistryAccess.Frozen> load(java.util.Map<net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>>, net.minecraft.resources.RegistryDataLoader.NetworkedRegistryData> p0, net.minecraft.server.packs.resources.ResourceProvider p1, java.util.List<net.minecraft.core.HolderLookup.RegistryLookup<?>> p2, java.util.List<net.minecraft.resources.RegistryDataLoader.RegistryData<?>> p3, java.util.concurrent.Executor p4) { kiln.Missing.called("net.minecraft.resources.RegistryDataLoader.load"); return null; }
    public static record RegistryData<T>(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key, com.mojang.serialization.Codec<T> elementCodec, net.minecraft.resources.RegistryValidator<T> validator) {
        public RegistryData(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> p0, com.mojang.serialization.Codec<T> p1) { this(p0, p1, (net.minecraft.resources.RegistryValidator) null); }
    }
    public static record NetworkedRegistryData(java.util.List<net.minecraft.core.RegistrySynchronization.PackedRegistryEntry> elements, net.minecraft.tags.TagNetworkSerialization.NetworkPayload tags) {
    }
}
