package net.minecraft.client.particle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Kiln's stand-in for {@code ParticleEngine}: the mods' particles, made by the providers they registered
 * ({@code ParticleProviderRegistry}) and ticked while they live. Horizon makes and draws the game's own particles;
 * these Kiln keeps and ticks but draws none of.
 */
public class ParticleEngine {
    private static final int MAX_PARTICLES = 16384;
    public static final List<ParticleRenderType> RENDER_ORDER = List.of(ParticleRenderType.SINGLE_QUADS,
            ParticleRenderType.ITEM_PICKUP, ParticleRenderType.ELDER_GUARDIANS);
    protected ClientLevel level;
    public final Map<ParticleRenderType, ParticleGroup<?>> particles = new IdentityHashMap<>();
    public final Queue<TrackingEmitter> trackingEmitters = new ArrayDeque<>();
    public final Queue<Particle> particlesToAdd = new ArrayDeque<>();
    public ParticleResources resourceManager;
    private final List<Particle> living = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    public ParticleEngine(ClientLevel level, ParticleResources resources) {
        this.level = level;
        this.resourceManager = resources;
    }

    /** Kiln's own: an engine of no level yet (the client's, until a level is set). */
    protected ParticleEngine() {
        this(null, null);
    }

    /** Particles following an entity: Kiln makes none (they are the game's, for the game's particle types). */
    public void createTrackingEmitter(Entity entity, ParticleOptions options) {}

    public void createTrackingEmitter(Entity entity, ParticleOptions options, int lifetime) {}

    /** A mod's particle from its provider, added; null for a type no mod registered a provider for. */
    public Particle createParticle(ParticleOptions options, double x, double y, double z, double xd, double yd,
            double zd) {
        Particle particle = makeParticle(options, x, y, z, xd, yd, zd);
        if (particle != null) {
            add(particle);
        }
        return particle;
    }

    public <T extends ParticleOptions> Particle makeParticle(T options, double x, double y, double z, double xd,
            double yd, double zd) {
        ParticleProvider<T> provider = kiln.ParticleProviders.provider(options.getType());
        return provider == null ? null : provider.createParticle(options, level, x, y, z, xd, yd, zd, random);
    }

    public void add(Particle particle) {
        if (particle != null) {
            synchronized (particlesToAdd) {
                particlesToAdd.add(particle);
            }
        }
    }

    /** A tick: each particle ticks, and the dead ones go. */
    public void tick() {
        synchronized (particlesToAdd) {
            while (!particlesToAdd.isEmpty() && living.size() < MAX_PARTICLES) {
                living.add(particlesToAdd.poll());
            }
            particlesToAdd.clear();
        }
        living.removeIf(particle -> {
            try {
                particle.tick();
            } catch (RuntimeException e) {
                particle.remove();
            }
            return !particle.isAlive();
        });
        particles.values().forEach(ParticleGroup::tickParticles);
    }

    protected void updateCount(ParticleLimit limit, int by) {}

    public void setLevel(ClientLevel level) {
        this.level = level;
        clearParticles();
    }

    public String countParticles() {
        return String.valueOf(living.size());
    }

    public void clearParticles() {
        living.clear();
        synchronized (particlesToAdd) {
            particlesToAdd.clear();
        }
        trackingEmitters.clear();
        particles.clear();
    }

    public RandomSource getRandom() {
        return random;
    }
}
