package net.fabricmc.fabric.api.client.particle.v1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for the Fabric API's {@code ParticleGroupRegistry}: a mod's own render types of particles and
 * the groups that hold them, by id. Kiln keeps them; it draws no particle of a mod's, so their order is kept only
 * as asked.
 */
public final class ParticleGroupRegistry {
    private static final Map<ParticleRenderType, Function<ParticleEngine, ParticleGroup<?>>> GROUPS =
            new ConcurrentHashMap<>();
    private static final Map<Identifier, ParticleRenderType> BY_ID = new ConcurrentHashMap<>();

    private ParticleGroupRegistry() {}

    public static void register(ParticleRenderType type, Function<ParticleEngine, ParticleGroup<?>> factory) {
        GROUPS.put(type, factory);
        BY_ID.putIfAbsent(idOf(type), type);
    }

    public static void registerOrdering(ParticleRenderType first, Identifier second) {}

    public static void registerOrdering(ParticleRenderType first, ParticleRenderType second) {}

    public static void registerOrdering(Identifier first, ParticleRenderType second) {}

    public static void registerOrdering(Identifier first, Identifier second) {}

    public static ParticleRenderType getParticleRenderType(Identifier id) {
        return BY_ID.get(id);
    }

    public static Identifier getId(ParticleRenderType type) {
        for (Map.Entry<Identifier, ParticleRenderType> entry : BY_ID.entrySet()) {
            if (entry.getValue().equals(type)) {
                return entry.getKey();
            }
        }
        return idOf(type);
    }

    /** A type's id from its name: {@code minecraft:single_quads}, or the mod's {@code namespace:path} as named. */
    private static Identifier idOf(ParticleRenderType type) {
        String name = type.name().toLowerCase(java.util.Locale.ROOT);
        return name.contains(":") ? Identifier.parse(name) : Identifier.withDefaultNamespace(name);
    }
}
