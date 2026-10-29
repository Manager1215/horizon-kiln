package net.fabricmc.api;

/** Kiln's stand-in for Fabric's {@code ClientModInitializer}: a mod's {@code client} entrypoint. */
@FunctionalInterface
public interface ClientModInitializer {
    void onInitializeClient();
}
