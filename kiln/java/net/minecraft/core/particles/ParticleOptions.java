package net.minecraft.core.particles;

/** Kiln's stand-in for {@code ParticleOptions}: a particle to add, its type and what that type needs. */
public interface ParticleOptions {
    ParticleType<?> getType();
}
