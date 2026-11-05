package net.minecraft.world.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code MobEffects}: the game's effects, by id and category, as entries of the registry. */
public final class MobEffects {
    public static final Holder<MobEffect> SPEED = register("speed", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> SLOWNESS = register("slowness", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> HASTE = register("haste", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> MINING_FATIGUE = register("mining_fatigue", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> STRENGTH = register("strength", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> INSTANT_HEALTH = register("instant_health", MobEffectCategory.BENEFICIAL, true);
    public static final Holder<MobEffect> INSTANT_DAMAGE = register("instant_damage", MobEffectCategory.HARMFUL, true);
    public static final Holder<MobEffect> JUMP_BOOST = register("jump_boost", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> NAUSEA = register("nausea", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> REGENERATION = register("regeneration", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> RESISTANCE = register("resistance", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> FIRE_RESISTANCE = register("fire_resistance", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> WATER_BREATHING = register("water_breathing", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> INVISIBILITY = register("invisibility", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> BLINDNESS = register("blindness", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> NIGHT_VISION = register("night_vision", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> HUNGER = register("hunger", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> WEAKNESS = register("weakness", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> POISON = register("poison", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> WITHER = register("wither", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> HEALTH_BOOST = register("health_boost", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> ABSORPTION = register("absorption", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> SATURATION = register("saturation", MobEffectCategory.BENEFICIAL, true);
    public static final Holder<MobEffect> GLOWING = register("glowing", MobEffectCategory.NEUTRAL);
    public static final Holder<MobEffect> LEVITATION = register("levitation", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> LUCK = register("luck", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> UNLUCK = register("unluck", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> SLOW_FALLING = register("slow_falling", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> CONDUIT_POWER = register("conduit_power", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> DOLPHINS_GRACE = register("dolphins_grace", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> BAD_OMEN = register("bad_omen", MobEffectCategory.NEUTRAL);
    public static final Holder<MobEffect> HERO_OF_THE_VILLAGE = register("hero_of_the_village", MobEffectCategory.BENEFICIAL);
    public static final Holder<MobEffect> DARKNESS = register("darkness", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> TRIAL_OMEN = register("trial_omen", MobEffectCategory.NEUTRAL);
    public static final Holder<MobEffect> RAID_OMEN = register("raid_omen", MobEffectCategory.NEUTRAL);
    public static final Holder<MobEffect> WIND_CHARGED = register("wind_charged", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> WEAVING = register("weaving", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> OOZING = register("oozing", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> INFESTED = register("infested", MobEffectCategory.HARMFUL);
    public static final Holder<MobEffect> BREATH_OF_THE_NAUTILUS = register("breath_of_the_nautilus", MobEffectCategory.BENEFICIAL);

    private MobEffects() {}

    private static Holder<MobEffect> register(String id, MobEffectCategory category) {
        return register(id, category, false);
    }

    private static Holder<MobEffect> register(String id, MobEffectCategory category, boolean instantaneous) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                ResourceKey.create(BuiltInRegistries.MOB_EFFECT.key(), Identifier.withDefaultNamespace(id)),
                MobEffect.kiln$game(category, instantaneous));
    }
}
