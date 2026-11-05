package net.minecraft.server.packs;

/** Kiln's stand-in for {@code PackResources}: a pack of resources (the game's, a mod's), by its id. */
public interface PackResources extends AutoCloseable {
    String packId();

    @Override
    default void close() {}
}
