package net.fabricmc.loader.api.entrypoint;

import net.fabricmc.loader.api.ModContainer;

/** Kiln's stand-in for Fabric Loader's {@code EntrypointContainer}: an entrypoint made, and the mod it is of. */
public interface EntrypointContainer<T> {
    T getEntrypoint();

    ModContainer getProvider();

    default String getDefinition() {
        return "";
    }
}
