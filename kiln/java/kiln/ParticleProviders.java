package kiln;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * The mods' particle providers, by type (the Fabric API's {@code ParticleProviderRegistry}). A provider that wants
 * its sprites ({@code PendingParticleProvider}) is made the first time it is asked for, with a set of one sprite named
 * as its type (Kiln has no particle atlas of the mods' textures): the particle has a sprite; Kiln draws none.
 */
public final class ParticleProviders {
    public static final ParticleProviderRegistry REGISTRY = new Registry();
    private static final Identifier ATLAS = Identifier.withDefaultNamespace("textures/atlas/particles.png");
    private static final Map<ParticleType<?>, ParticleProvider<?>> MADE = new ConcurrentHashMap<>();
    private static final Map<ParticleType<?>, ParticleProviderRegistry.PendingParticleProvider<?>> PENDING =
            new ConcurrentHashMap<>();

    private ParticleProviders() {}

    /** The provider a mod registered for the type, or null. */
    @SuppressWarnings("unchecked")
    public static <T extends ParticleOptions> ParticleProvider<T> provider(ParticleType<?> type) {
        if (type == null) {
            return null;
        }
        ParticleProvider<?> made = MADE.get(type);
        if (made == null) {
            ParticleProviderRegistry.PendingParticleProvider<?> pending = PENDING.remove(type);
            if (pending != null) {
                made = pending.create(new OneSprite(type));
                if (made != null) {
                    MADE.put(type, made);
                }
            }
        }
        return (ParticleProvider<T>) made;
    }

    /** Whether a mod registered a provider for the type. */
    public static boolean has(ParticleType<?> type) {
        return MADE.containsKey(type) || PENDING.containsKey(type);
    }

    private static final class Registry implements ParticleProviderRegistry {
        @Override
        public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider) {
            PENDING.remove(type);
            MADE.put(type, provider);
        }

        @Override
        public <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleProvider<T> provider) {
            MADE.remove(type);
            PENDING.put(type, provider);
        }
    }

    /** The sprites of a type: one, named as the type ({@code namespace:path}). */
    private static final class OneSprite implements FabricSpriteSet {
        private final TextureAtlasSprite sprite;

        OneSprite(ParticleType<?> type) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
            this.sprite = TextureAtlasSprite.kiln$of(ATLAS, id != null ? id : Identifier.withDefaultNamespace("missing"));
        }

        @Override
        public TextureAtlasSprite get(int age, int lifetime) {
            return sprite;
        }

        @Override
        public TextureAtlasSprite get(RandomSource random) {
            return sprite;
        }

        @Override
        public TextureAtlasSprite first() {
            return sprite;
        }

        @Override
        public TextureAtlas getAtlas() {
            return new TextureAtlas(ATLAS);
        }

        @Override
        public List<TextureAtlasSprite> getSprites() {
            return List.of(sprite);
        }
    }
}
