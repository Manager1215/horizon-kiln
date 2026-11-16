package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Kiln's stand-in for {@code Fireworks}: a firework rocket's flight duration and explosions
 * ({@code minecraft:fireworks}), read and written as the game saves them.
 */
public record Fireworks(int flightDuration, List<FireworkExplosion> explosions) {
    public static final int MAX_EXPLOSIONS = 256;
    public static final Codec<Fireworks> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("flight_duration", 0).forGetter(Fireworks::flightDuration),
            FireworkExplosion.CODEC.sizeLimitedListOf(MAX_EXPLOSIONS).optionalFieldOf("explosions", List.of())
                    .forGetter(Fireworks::explosions))
            .apply(i, Fireworks::new));

    public Fireworks {
        explosions = List.copyOf(explosions);
    }
}
