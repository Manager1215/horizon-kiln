package net.minecraft.world.item.alchemy;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Kiln's stand-in for {@code Potions}: the game's potions, with vanilla's effects, durations (in ticks) and
 * amplifiers, as entries of {@link BuiltInRegistries#POTION}, so a saved {@code potion_contents} names one of them.
 */
public final class Potions {
    public static final Holder<Potion> WATER = register("water", "water");
    public static final Holder<Potion> MUNDANE = register("mundane", "mundane");
    public static final Holder<Potion> THICK = register("thick", "thick");
    public static final Holder<Potion> AWKWARD = register("awkward", "awkward");
    public static final Holder<Potion> NIGHT_VISION = register("night_vision", "night_vision", effect(MobEffects.NIGHT_VISION, 3600, 0));
    public static final Holder<Potion> LONG_NIGHT_VISION = register("long_night_vision", "night_vision", effect(MobEffects.NIGHT_VISION, 9600, 0));
    public static final Holder<Potion> INVISIBILITY = register("invisibility", "invisibility", effect(MobEffects.INVISIBILITY, 3600, 0));
    public static final Holder<Potion> LONG_INVISIBILITY = register("long_invisibility", "invisibility", effect(MobEffects.INVISIBILITY, 9600, 0));
    public static final Holder<Potion> LEAPING = register("leaping", "leaping", effect(MobEffects.JUMP_BOOST, 3600, 0));
    public static final Holder<Potion> LONG_LEAPING = register("long_leaping", "leaping", effect(MobEffects.JUMP_BOOST, 9600, 0));
    public static final Holder<Potion> STRONG_LEAPING = register("strong_leaping", "leaping", effect(MobEffects.JUMP_BOOST, 1800, 1));
    public static final Holder<Potion> FIRE_RESISTANCE = register("fire_resistance", "fire_resistance", effect(MobEffects.FIRE_RESISTANCE, 3600, 0));
    public static final Holder<Potion> LONG_FIRE_RESISTANCE = register("long_fire_resistance", "fire_resistance", effect(MobEffects.FIRE_RESISTANCE, 9600, 0));
    public static final Holder<Potion> SWIFTNESS = register("swiftness", "swiftness", effect(MobEffects.SPEED, 3600, 0));
    public static final Holder<Potion> LONG_SWIFTNESS = register("long_swiftness", "swiftness", effect(MobEffects.SPEED, 9600, 0));
    public static final Holder<Potion> STRONG_SWIFTNESS = register("strong_swiftness", "swiftness", effect(MobEffects.SPEED, 1800, 1));
    public static final Holder<Potion> SLOWNESS = register("slowness", "slowness", effect(MobEffects.SLOWNESS, 1800, 0));
    public static final Holder<Potion> LONG_SLOWNESS = register("long_slowness", "slowness", effect(MobEffects.SLOWNESS, 4800, 0));
    public static final Holder<Potion> STRONG_SLOWNESS = register("strong_slowness", "slowness", effect(MobEffects.SLOWNESS, 400, 3));
    public static final Holder<Potion> TURTLE_MASTER = register("turtle_master", "turtle_master",
            effect(MobEffects.SLOWNESS, 400, 3), effect(MobEffects.RESISTANCE, 400, 2));
    public static final Holder<Potion> LONG_TURTLE_MASTER = register("long_turtle_master", "turtle_master",
            effect(MobEffects.SLOWNESS, 800, 3), effect(MobEffects.RESISTANCE, 800, 2));
    public static final Holder<Potion> STRONG_TURTLE_MASTER = register("strong_turtle_master", "turtle_master",
            effect(MobEffects.SLOWNESS, 400, 5), effect(MobEffects.RESISTANCE, 400, 3));
    public static final Holder<Potion> WATER_BREATHING = register("water_breathing", "water_breathing", effect(MobEffects.WATER_BREATHING, 3600, 0));
    public static final Holder<Potion> LONG_WATER_BREATHING = register("long_water_breathing", "water_breathing", effect(MobEffects.WATER_BREATHING, 9600, 0));
    public static final Holder<Potion> HEALING = register("healing", "healing", effect(MobEffects.INSTANT_HEALTH, 1, 0));
    public static final Holder<Potion> STRONG_HEALING = register("strong_healing", "healing", effect(MobEffects.INSTANT_HEALTH, 1, 1));
    public static final Holder<Potion> HARMING = register("harming", "harming", effect(MobEffects.INSTANT_DAMAGE, 1, 0));
    public static final Holder<Potion> STRONG_HARMING = register("strong_harming", "harming", effect(MobEffects.INSTANT_DAMAGE, 1, 1));
    public static final Holder<Potion> POISON = register("poison", "poison", effect(MobEffects.POISON, 900, 0));
    public static final Holder<Potion> LONG_POISON = register("long_poison", "poison", effect(MobEffects.POISON, 1800, 0));
    public static final Holder<Potion> STRONG_POISON = register("strong_poison", "poison", effect(MobEffects.POISON, 432, 1));
    public static final Holder<Potion> REGENERATION = register("regeneration", "regeneration", effect(MobEffects.REGENERATION, 900, 0));
    public static final Holder<Potion> LONG_REGENERATION = register("long_regeneration", "regeneration", effect(MobEffects.REGENERATION, 1800, 0));
    public static final Holder<Potion> STRONG_REGENERATION = register("strong_regeneration", "regeneration", effect(MobEffects.REGENERATION, 450, 1));
    public static final Holder<Potion> STRENGTH = register("strength", "strength", effect(MobEffects.STRENGTH, 3600, 0));
    public static final Holder<Potion> LONG_STRENGTH = register("long_strength", "strength", effect(MobEffects.STRENGTH, 9600, 0));
    public static final Holder<Potion> STRONG_STRENGTH = register("strong_strength", "strength", effect(MobEffects.STRENGTH, 1800, 1));
    public static final Holder<Potion> WEAKNESS = register("weakness", "weakness", effect(MobEffects.WEAKNESS, 1800, 0));
    public static final Holder<Potion> LONG_WEAKNESS = register("long_weakness", "weakness", effect(MobEffects.WEAKNESS, 4800, 0));
    public static final Holder<Potion> LUCK = register("luck", "luck", effect(MobEffects.LUCK, 6000, 0));
    public static final Holder<Potion> SLOW_FALLING = register("slow_falling", "slow_falling", effect(MobEffects.SLOW_FALLING, 1800, 0));
    public static final Holder<Potion> LONG_SLOW_FALLING = register("long_slow_falling", "slow_falling", effect(MobEffects.SLOW_FALLING, 4800, 0));
    public static final Holder<Potion> WIND_CHARGED = register("wind_charged", "wind_charged", effect(MobEffects.WIND_CHARGED, 3600, 0));
    public static final Holder<Potion> WEAVING = register("weaving", "weaving", effect(MobEffects.WEAVING, 3600, 0));
    public static final Holder<Potion> OOZING = register("oozing", "oozing", effect(MobEffects.OOZING, 3600, 0));
    public static final Holder<Potion> INFESTED = register("infested", "infested", effect(MobEffects.INFESTED, 3600, 0));

    private Potions() {}

    private static MobEffectInstance effect(Holder<MobEffect> effect, int duration, int amplifier) {
        return new MobEffectInstance(effect, duration, amplifier);
    }

    private static Holder<Potion> register(String id, String name, MobEffectInstance... effects) {
        return Registry.registerForHolder(BuiltInRegistries.POTION, Identifier.withDefaultNamespace(id),
                new Potion(name, effects));
    }
}
