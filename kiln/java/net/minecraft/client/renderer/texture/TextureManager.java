package net.minecraft.client.renderer.texture;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Kiln's stand-in for {@code TextureManager}: the textures by id, the ones mods registered and the ones asked for (from
 * the resources, as vanilla loads them). The game draws with its own textures: Kiln uploads none to the GPU; a
 * registered {@link DynamicTexture}'s pixels go to the game as a mod's picture (kiln.Sprites, D-179).
 */
public class TextureManager implements AutoCloseable {
    private final Map<Identifier, AbstractTexture> byPath = new HashMap<>();
    private final ResourceManager resources;

    public TextureManager(ResourceManager resources) {
        this.resources = resources;
    }

    public synchronized void register(Identifier id, AbstractTexture texture) {
        AbstractTexture old = byPath.put(id, texture);
        if (old != null && old != texture) {
            old.close();
        }
        kiln.Sprites.dynamic(id, texture instanceof DynamicTexture dynamic ? dynamic : null);
    }

    /** The texture of {@code id}: one registered, or one from the resource of that id (registered now). */
    public synchronized AbstractTexture getTexture(Identifier id) {
        return byPath.computeIfAbsent(id, SimpleTexture::new);
    }

    public synchronized void release(Identifier id) {
        AbstractTexture old = byPath.remove(id);
        if (old != null) {
            old.close();
        }
        kiln.Sprites.dynamic(id, null);
    }

    public void tick() {}

    @Override
    public synchronized void close() {
        byPath.keySet().forEach(id -> kiln.Sprites.dynamic(id, null));
        byPath.values().forEach(AbstractTexture::close);
        byPath.clear();
    }

    /** Kiln's own: the resources textures load from. */
    public ResourceManager kiln$resources() {
        return resources;
    }
}
