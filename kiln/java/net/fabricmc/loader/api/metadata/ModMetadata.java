package net.fabricmc.loader.api.metadata;

import net.fabricmc.loader.api.Version;

/** Kiln's stand-in for Fabric Loader's {@code ModMetadata}: what a mod's fabric.mod.json says of it. */
public interface ModMetadata {
    String getId();

    Version getVersion();

    String getName();

    String getDescription();

    /** The mod's icon in the jar for a size, as its fabric.mod.json names it; none for the mods Kiln stands in for. */
    default java.util.Optional<String> getIconPath(int size) {
        return java.util.Optional.empty();
    }
}
