package net.fabricmc.fabric.api.client.particle.v1;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/**
 * Kiln's stand-in for the Fabric API's {@code ParticleProviderRegistry}: the providers that make a mod's particles
 * on the client, by type ({@code kiln.ParticleProviders}); {@code ParticleEngine.createParticle} uses them.
 */
public interface ParticleProviderRegistry {
    static ParticleProviderRegistry getInstance() {
        return kiln.ParticleProviders.REGISTRY;
    }

    <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider);

    <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleProvider<T> provider);

    @FunctionalInterface
    interface PendingParticleProvider<T extends ParticleOptions> {
        ParticleProvider<T> create(FabricSpriteSet sprites);
    }
}
