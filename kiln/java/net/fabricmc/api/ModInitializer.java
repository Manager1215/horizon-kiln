package net.fabricmc.api;

/** Kiln's stand-in for Fabric's {@code ModInitializer}: a mod's {@code main} entrypoint, run before the client ones. */
public interface ModInitializer {
    void onInitialize();
}
