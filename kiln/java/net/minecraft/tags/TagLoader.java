package net.minecraft.tags;

/**
 * Kiln's stand-in for {@code TagLoader}: reads a registry's tags from data packs. Kiln reads the game's own tags itself
 * ({@code kiln.GameTags}, from its prepared resources) and a server call asks the game, so loading them again from a
 * resource manager has nothing to add: a mod that does (REI's local recipes, D-217) finds every registry as it is.
 */
public class TagLoader<T> {
    public TagLoader(net.minecraft.tags.TagLoader.ElementLookup<T> p0, java.lang.String p1) {  }
    public static java.util.List<net.minecraft.core.Registry.PendingTags<?>> loadTagsForExistingRegistries(net.minecraft.server.packs.resources.ResourceManager p0, net.minecraft.core.RegistryAccess p1) { return java.util.List.of(); }
    public static <T> void loadTagsForRegistry(net.minecraft.server.packs.resources.ResourceManager p0, net.minecraft.core.WritableRegistry<T> p1) {}
    public static java.util.List<net.minecraft.core.HolderLookup.RegistryLookup<?>> buildUpdatedLookups(net.minecraft.core.RegistryAccess.Frozen p0, java.util.List<net.minecraft.core.Registry.PendingTags<?>> p1) { kiln.Missing.called("net.minecraft.tags.TagLoader.buildUpdatedLookups"); return null; }
    protected TagLoader() {  }
    public static interface ElementLookup<T> {
        java.util.Optional<? extends T> get(net.minecraft.resources.Identifier p0, boolean p1);
    }
    public static record EntryWithSource(net.minecraft.tags.TagEntry entry, java.lang.String source) {
    }
    public static record LoadResult<T>(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key, java.util.Map<net.minecraft.tags.TagKey<T>, java.util.List<net.minecraft.core.Holder<T>>> tags) {
    }
    public static record SortingEntry(java.util.List<net.minecraft.tags.TagLoader.EntryWithSource> entries) implements net.minecraft.util.DependencySorter.Entry<net.minecraft.resources.Identifier> {
        public void visitRequiredDependencies(java.util.function.Consumer<net.minecraft.resources.Identifier> p0) { kiln.Missing.called("net.minecraft.tags.TagLoader.SortingEntry.visitRequiredDependencies"); }
        public void visitOptionalDependencies(java.util.function.Consumer<net.minecraft.resources.Identifier> p0) { kiln.Missing.called("net.minecraft.tags.TagLoader.SortingEntry.visitOptionalDependencies"); }
    }
}
