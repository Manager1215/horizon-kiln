package net.fabricmc.loader.api;

import net.fabricmc.loader.api.metadata.ModMetadata;

/** Kiln's stand-in for Fabric Loader's {@code ModContainer}: a mod Java runs. */
public interface ModContainer {
    ModMetadata getMetadata();

    /** A file of the mod (its icon, for a config screen or JEI): none where Kiln doesn't know the mod's jar. */
    default java.util.Optional<java.nio.file.Path> findPath(String file) {
        return java.util.Optional.empty();
    }
    /**
     * The mod's folder, or its jar's root (a zip file system): {@code kiln.Mods}' containers say theirs (#271); none
     * for another.
     */
    default java.util.List<java.nio.file.Path> getRootPaths() {
        return java.util.List.of();
    }

    /** Where the mod comes from (its jar file or folder): Collective reads its jar through it (#271). */
    default net.fabricmc.loader.api.metadata.ModOrigin getOrigin() {
        return kiln.Mods.origin(getMetadata().getId(), null);
    }

    default java.util.Optional<net.fabricmc.loader.api.ModContainer> getContainingMod() {
        return java.util.Optional.empty();
    }

    default java.util.Collection<net.fabricmc.loader.api.ModContainer> getContainedMods() {
        return java.util.List.of();
    }

    @Deprecated
    default java.nio.file.Path getRoot() {
        return getRootPath();
    }

    @Deprecated
    default java.nio.file.Path getRootPath() {
        java.util.List<java.nio.file.Path> roots = getRootPaths();
        return roots.isEmpty() ? null : roots.get(0);
    }

    @Deprecated
    default java.nio.file.Path getPath(java.lang.String file) {
        java.nio.file.Path root = getRootPath();
        return root == null ? null : root.resolve(file.startsWith("/") ? file.substring(1) : file);
    }
}
