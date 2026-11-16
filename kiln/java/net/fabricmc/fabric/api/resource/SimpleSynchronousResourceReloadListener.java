package net.fabricmc.fabric.api.resource;

/**
 * Kiln's stand-in for Fabric's {@code SimpleSynchronousResourceReloadListener} (resource loader v0): a reload listener
 * that reads the resources at once, with an id.
 */
public interface SimpleSynchronousResourceReloadListener extends IdentifiableResourceReloadListener,
        net.minecraft.server.packs.resources.ResourceManagerReloadListener {}
