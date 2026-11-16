package net.minecraft.client.resources.model.sprite;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code AtlasManager}: the game's texture atlases, by id. Horizon stitches and draws the atlases;
 * a mod gets one to name its sprites by (kiln.Sprites), and Kiln's drawing sends the sprite's name to Horizon.
 */
public class AtlasManager implements AutoCloseable, net.minecraft.server.packs.resources.PreparableReloadListener,
        SpriteGetter {
    private final Map<Identifier, TextureAtlas> atlases = new ConcurrentHashMap<>();

    public AtlasManager(net.minecraft.client.renderer.texture.TextureManager textures, int maxMipmapLevels) {}

    protected AtlasManager() {}

    public TextureAtlas getAtlasOrThrow(Identifier id) {
        return atlases.computeIfAbsent(id, TextureAtlas::new);
    }

    public TextureAtlasSprite get(SpriteId sprite) {
        return getAtlasOrThrow(sprite.atlasLocation()).getSprite(sprite.texture());
    }

    @Override
    public void close() {}

    @Override
    public java.util.concurrent.CompletableFuture<Void> reload(
            net.minecraft.server.packs.resources.PreparableReloadListener.SharedState state,
            java.util.concurrent.Executor prepare,
            net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier barrier,
            java.util.concurrent.Executor apply) {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
}
