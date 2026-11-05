package net.minecraft.client.renderer.texture;

/**
 * Kiln's stand-in for {@code AbstractTexture}: a texture the texture manager keeps. The game draws with its own
 * textures: Kiln uploads none to the GPU.
 */
public abstract class AbstractTexture implements AutoCloseable {
    public AbstractTexture() {}

    @Override
    public void close() {}
}
