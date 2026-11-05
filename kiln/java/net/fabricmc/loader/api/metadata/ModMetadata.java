package net.fabricmc.loader.api.metadata;

import net.fabricmc.loader.api.Version;

/** Kiln's stand-in for Fabric Loader's {@code ModMetadata}: what a mod's fabric.mod.json says of it. */
public interface ModMetadata {
    String getId();

    Version getVersion();

    String getName();

    String getDescription();
}
