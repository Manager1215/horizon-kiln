package net.minecraft.core.particles;

/** Kiln's stand-in for {@code ParticleType}: a kind of particle, registered by id; the game draws the game's own. */
public abstract class ParticleType<T extends ParticleOptions> {
    private final boolean overrideLimiter;

    protected ParticleType(boolean overrideLimiter) {
        this.overrideLimiter = overrideLimiter;
    }

    public boolean getOverrideLimiter() {
        return overrideLimiter;
    }
}
