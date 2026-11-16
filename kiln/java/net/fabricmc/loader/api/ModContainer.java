package net.fabricmc.loader.api;

import net.fabricmc.loader.api.metadata.ModMetadata;

/** Kiln's stand-in for Fabric Loader's {@code ModContainer}: a mod Java runs. */
public interface ModContainer {
    ModMetadata getMetadata();

    /** A file of the mod (its icon, for a config screen or JEI): none where Kiln doesn't know the mod's jar. */
    default java.util.Optional<java.nio.file.Path> findPath(String file) {
        return java.util.Optional.empty();
    }
}
