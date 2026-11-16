package net.minecraft.client.renderer.texture;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code TextureAtlas}: one of the game's atlases and its sprites, by id, each the size of its
 * picture (kiln.Sprites). Horizon stitches and draws the atlas; Kiln's sprites only name and measure.
 */
public class TextureAtlas extends AbstractTexture implements TickableTexture, Dumpable {
    public static final Identifier LOCATION_BLOCKS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    private final Identifier location;
    private final Map<Identifier, TextureAtlasSprite> sprites = new ConcurrentHashMap<>();

    public TextureAtlas(Identifier location) {
        this.location = location;
    }

    protected TextureAtlas() {
        this(Identifier.withDefaultNamespace("blocks"));
    }

    public Identifier location() {
        return location;
    }

    public TextureAtlasSprite getSprite(Identifier name) {
        return sprites.computeIfAbsent(name, n -> {
            int[] size = kiln.Sprites.size(location, n);
            return new TextureAtlasSprite(location, new SpriteContents(n, size[0], size[1], location), size[0], size[1], 0, 0, 0);
        });
    }

    @Override
    public void tick() {}

    @Override
    public void dumpContents(Identifier id, java.nio.file.Path dir) {}
}
