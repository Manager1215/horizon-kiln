package net.minecraft.core.particles;

/** Kiln's stand-in for {@code SimpleParticleType}: a particle type that needs nothing more, its own options. */
public class SimpleParticleType extends ParticleType<SimpleParticleType> implements ParticleOptions {
    protected SimpleParticleType(boolean overrideLimiter) {
        super(overrideLimiter);
    }

    @Override
    public SimpleParticleType getType() {
        return this;
    }

    /** Kiln's own: the game's particle type of this id, registered. */
    static SimpleParticleType kiln$register(String id, boolean overrideLimiter) {
        return net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE,
                net.minecraft.resources.Identifier.withDefaultNamespace(id), new SimpleParticleType(overrideLimiter));
    }
}
