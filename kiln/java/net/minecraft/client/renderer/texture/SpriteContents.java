package net.minecraft.client.renderer.texture;

import net.minecraft.resources.Identifier;

/** Kiln's stand-in for {@code SpriteContents}: a sprite's name and its size in pixels (one frame's). */
public class SpriteContents implements AutoCloseable, Stitcher.Entry {
    private final Identifier name;
    private final int width;
    private final int height;
    /** The atlas it is of, for its {@code .mcmeta}; null when unknown. */
    private final Identifier atlas;

    /** Kiln's own: a sprite named {@code name}, {@code width} by {@code height}. */
    public SpriteContents(Identifier name, int width, int height) {
        this(name, width, height, null);
    }

    /** Kiln's own: a sprite named {@code name} of atlas {@code atlas}, {@code width} by {@code height}. */
    public SpriteContents(Identifier name, int width, int height, Identifier atlas) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.atlas = atlas;
    }

    protected SpriteContents() {
        this(Identifier.withDefaultNamespace("missingno"), 16, 16);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public Identifier name() {
        return name;
    }

    /**
     * A section of the sprite's {@code .mcmeta} beyond its animation: {@code gui} (how a GUI sprite scales, JEI's
     * ScalableDrawable reads it) from the mod's jar or the game's resources (kiln.Sprites, D-160); none of the others.
     */
    @SuppressWarnings("unchecked")
    public <T> java.util.Optional<T> getAdditionalMetadata(
            net.minecraft.server.packs.metadata.MetadataSectionType<T> type) {
        if (atlas == null || type == null || !"gui".equals(type.name())) {
            return java.util.Optional.empty();
        }
        com.google.gson.JsonObject gui = kiln.Sprites.guiMetadata(atlas, name);
        return gui == null ? java.util.Optional.empty()
                : java.util.Optional.of((T) net.minecraft.client.resources.metadata.gui.GuiMetadataSection.of(gui));
    }

    @Override
    public void close() {}
}
