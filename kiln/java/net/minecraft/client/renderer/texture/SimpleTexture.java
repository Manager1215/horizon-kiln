package net.minecraft.client.renderer.texture;

import net.minecraft.resources.Identifier;

/** Kiln's stand-in for {@code SimpleTexture}: a texture from a resource, by its id ({@code minecraft:textures/...}). */
public class SimpleTexture extends AbstractTexture {
    private final Identifier location;

    public SimpleTexture(Identifier location) {
        this.location = location;
    }

    /** Kiln's own: the resource it is from. */
    public Identifier kiln$location() {
        return location;
    }
}
