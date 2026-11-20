package net.minecraft.client.particle;

import java.util.ArrayDeque;
import java.util.Queue;

/** Kiln's stand-in for {@code ParticleGroup}: particles of one render type, ticked together. */
public abstract class ParticleGroup<P extends Particle> {
    private static final int LIMIT = 16384;
    protected final ParticleEngine engine;
    public final Queue<P> particles = new ArrayDeque<>();

    public ParticleGroup(ParticleEngine engine) {
        this.engine = engine;
    }

    /** Kiln's own: a group of no engine (a generated subclass's). */
    protected ParticleGroup() {
        this(null);
    }

    public boolean isEmpty() {
        return particles.isEmpty();
    }

    public void tickParticles() {
        particles.removeIf(particle -> {
            tickParticle(particle);
            return !particle.isAlive();
        });
    }

    public void tickParticle(Particle particle) {
        try {
            particle.tick();
        } catch (RuntimeException e) {
            particle.remove();
        }
    }

    @SuppressWarnings("unchecked")
    public boolean add(Particle particle) {
        if (particles.size() >= LIMIT) {
            return false;
        }
        particles.add((P) particle);
        return true;
    }

    public int size() {
        return particles.size();
    }

    public abstract net.minecraft.client.renderer.state.level.ParticleGroupRenderState extractRenderState(
            net.minecraft.client.renderer.culling.Frustum frustum, net.minecraft.client.Camera camera,
            float partialTick);
}
