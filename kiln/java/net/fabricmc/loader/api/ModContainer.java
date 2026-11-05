package net.fabricmc.loader.api;

import net.fabricmc.loader.api.metadata.ModMetadata;

/** Kiln's stand-in for Fabric Loader's {@code ModContainer}: a mod Java runs. */
public interface ModContainer {
    ModMetadata getMetadata();
}
