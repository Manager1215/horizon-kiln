package net.minecraft.client.renderer.texture;

import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code TextureAtlasSprite}: a sprite of an atlas, its contents (name and size). Its texture
 * coordinates are the whole sprite's (0 to 1): Horizon places the sprite in its atlas when it draws it.
 */
public class TextureAtlasSprite implements UvMapping, AutoCloseable {
    private final Identifier atlasLocation;
    private final SpriteContents contents;
    private final int x;
    private final int y;

    protected TextureAtlasSprite(Identifier atlasLocation, SpriteContents contents, int atlasWidth, int atlasHeight,
            int x, int y, int padding) {
        this.atlasLocation = atlasLocation;
        this.contents = contents;
        this.x = x;
        this.y = y;
    }

    protected TextureAtlasSprite() {
        this(Identifier.withDefaultNamespace("blocks"), new SpriteContents(Identifier.withDefaultNamespace("missingno"), 16, 16), 16, 16, 0, 0, 0);
    }

    /**
     * Kiln's own: sprite {@code name} of atlas {@code atlas} ({@code minecraft:block/water_still} of the blocks'), its
     * size its picture's (an animated one's frame) from the mods' jars or the game's resources (D-168).
     */
    public static TextureAtlasSprite kiln$of(Identifier atlas, Identifier name) {
        int[] size = kiln.Sprites.size(atlas, name);
        return new TextureAtlasSprite(atlas, new SpriteContents(name, size[0], size[1], atlas), size[0], size[1], 0, 0,
                0);
    }

    public SpriteContents contents() {
        return contents;
    }

    public Identifier atlasLocation() {
        return atlasLocation;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public float getU0() {
        return 0;
    }

    public float getU1() {
        return 1;
    }

    public float getV0() {
        return 0;
    }

    public float getV1() {
        return 1;
    }

    @Override
    public float getU(float offset) {
        return offset;
    }

    @Override
    public float getV(float offset) {
        return offset;
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
        return "TextureAtlasSprite{name='" + contents.name() + "', atlas='" + atlasLocation + "'}";
    }
}
