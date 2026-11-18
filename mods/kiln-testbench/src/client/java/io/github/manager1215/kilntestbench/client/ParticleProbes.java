package io.github.manager1215.kilntestbench.client;

import static io.github.manager1215.kilntestbench.client.Game.check;
import static io.github.manager1215.kilntestbench.client.Game.expect;
import static io.github.manager1215.kilntestbench.core.Bench.Phase.INIT;

import io.github.manager1215.kilntestbench.core.Bench;
import io.github.manager1215.kilntestbench.core.Probe;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** The game's particle types that need nothing more: their ids in the registry, and which ignore the particle limit. */
final class ParticleProbes {
    private ParticleProbes() {}

    /** The simple particle types the game spawns however many there are already (their override of the limit). */
    private static final Set<String> OVERRIDE_LIMITER = Set.of("damage_indicator", "elder_guardian",
            "explosion_emitter", "explosion", "gust", "gust_emitter_large", "gust_emitter_small", "sonic_boom",
            "sculk_charge_pop", "poof", "spit", "squid_ink", "sweep_attack", "campfire_cosy_smoke",
            "campfire_signal_smoke", "glow_squid_ink", "glow", "wax_on", "wax_off", "electric_spark", "scrape",
            "trial_spawner_detection", "trial_spawner_detection_ominous", "vault_connection", "ominous_spawning");

    /** The constants whose ids aren't their names. */
    private static final Map<String, String> RENAMED = Map.of("TRIAL_SPAWNER_DETECTED_PLAYER", "trial_spawner_detection",
            "TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS", "trial_spawner_detection_ominous");

    private static String id(String field) {
        return RENAMED.getOrDefault(field, field.toLowerCase(Locale.ROOT));
    }

    /** Each public constant of {@code ParticleTypes} that is a simple particle type, with its field's name. */
    private static List<Object[]> simple() throws IllegalAccessException {
        List<Object[]> out = new ArrayList<>();
        for (Field field : ParticleTypes.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof SimpleParticleType type) {
                out.add(new Object[] {field.getName(), type});
            }
        }
        return out;
    }

    static void add(Bench b) {
        b.add("particle.ids", INIT, () -> new Probe() {
            public Object run() throws Exception {
                // Each constant is registered under its own name (HAPPY_VILLAGER as minecraft:happy_villager, but for
                // the trial spawner's two), and the registry gives the constant back for it: two readings of one id.
                List<Object[]> types = simple();
                check(types.size() >= 60, "only " + types.size() + " simple particle types");
                for (Object[] t : types) {
                    SimpleParticleType type = (SimpleParticleType) t[1];
                    Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
                    expect(id, "minecraft:" + id((String) t[0]));
                    check(BuiltInRegistries.PARTICLE_TYPE.getValue(id) == type, id + " gives another type back");
                    check(type.getType() == type, id + "'s options are another type's");
                }
                return types.size() + " types";
            }
        });
        b.add("particle.limiter", INIT, () -> new Probe() {
            public Object run() throws Exception {
                int over = 0;
                for (Object[] t : simple()) {
                    String name = id((String) t[0]);
                    boolean overrides = ((SimpleParticleType) t[1]).getOverrideLimiter();
                    check(overrides == OVERRIDE_LIMITER.contains(name), name + " overrides the limit: " + overrides);
                    over += overrides ? 1 : 0;
                }
                return expect(over, OVERRIDE_LIMITER.size());
            }
        });
    }
}
