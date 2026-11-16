package net.minecraft.server.packs.resources;

/** Kiln's stand-in for {@code CloseableResourceManager}: a resource manager that a reload closes after it. */
public interface CloseableResourceManager extends ResourceManager, AutoCloseable {
    @Override
    void close();
}
