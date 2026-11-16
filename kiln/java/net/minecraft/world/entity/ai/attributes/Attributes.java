package net.minecraft.world.entity.ai.attributes;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Kiln's stand-in for {@code Attributes}: the game's attributes, by id, with vanilla's defaults and sentiments
 * (D-177: burning time and fall damage are bad to have more of; gravity, scale and the waypoint ranges neutral).
 */
public class Attributes {
    private static final Map<Identifier, Holder<Attribute>> BY_ID = new ConcurrentHashMap<>();

    public static final Holder<Attribute> AIR_DRAG_MODIFIER = attribute("air_drag_modifier", 1.0);
    public static final Holder<Attribute> ARMOR = attribute("armor", 0.0);
    public static final Holder<Attribute> ARMOR_TOUGHNESS = attribute("armor_toughness", 0.0);
    public static final Holder<Attribute> ATTACK_DAMAGE = attribute("attack_damage", 2.0);
    public static final Holder<Attribute> ATTACK_KNOCKBACK = attribute("attack_knockback", 0.0);
    public static final Holder<Attribute> ATTACK_SPEED = attribute("attack_speed", 4.0);
    public static final Holder<Attribute> BLOCK_BREAK_SPEED = attribute("block_break_speed", 1.0);
    public static final Holder<Attribute> BLOCK_INTERACTION_RANGE = attribute("block_interaction_range", 4.5);
    public static final Holder<Attribute> BURNING_TIME = attribute("burning_time", 1.0, Attribute.Sentiment.NEGATIVE);
    public static final Holder<Attribute> ENTITY_INTERACTION_RANGE = attribute("entity_interaction_range", 3.0);
    public static final Holder<Attribute> EXPLOSION_KNOCKBACK_RESISTANCE =
            attribute("explosion_knockback_resistance", 0.0);
    public static final Holder<Attribute> FALL_DAMAGE_MULTIPLIER =
            attribute("fall_damage_multiplier", 1.0, Attribute.Sentiment.NEGATIVE);
    public static final Holder<Attribute> FLYING_SPEED = attribute("flying_speed", 0.4);
    public static final Holder<Attribute> FOLLOW_RANGE = attribute("follow_range", 32.0);
    public static final Holder<Attribute> GRAVITY = attribute("gravity", 0.08, Attribute.Sentiment.NEUTRAL);
    public static final Holder<Attribute> JUMP_STRENGTH = attribute("jump_strength", 0.42);
    public static final Holder<Attribute> KNOCKBACK_RESISTANCE = attribute("knockback_resistance", 0.0);
    public static final Holder<Attribute> LUCK = attribute("luck", 0.0);
    public static final Holder<Attribute> MAX_ABSORPTION = attribute("max_absorption", 0.0);
    public static final Holder<Attribute> MAX_HEALTH = attribute("max_health", 20.0);
    public static final Holder<Attribute> MINING_EFFICIENCY = attribute("mining_efficiency", 0.0);
    public static final Holder<Attribute> MOVEMENT_EFFICIENCY = attribute("movement_efficiency", 0.0);
    public static final Holder<Attribute> MOVEMENT_SPEED = attribute("movement_speed", 0.7);
    public static final Holder<Attribute> OXYGEN_BONUS = attribute("oxygen_bonus", 0.0);
    public static final Holder<Attribute> SAFE_FALL_DISTANCE = attribute("safe_fall_distance", 3.0);
    public static final Holder<Attribute> SCALE = attribute("scale", 1.0, Attribute.Sentiment.NEUTRAL);
    public static final Holder<Attribute> SNEAKING_SPEED = attribute("sneaking_speed", 0.3);
    public static final Holder<Attribute> STEP_HEIGHT = attribute("step_height", 0.6);
    public static final Holder<Attribute> SUBMERGED_MINING_SPEED = attribute("submerged_mining_speed", 0.2);
    public static final Holder<Attribute> SWEEPING_DAMAGE_RATIO = attribute("sweeping_damage_ratio", 0.0);
    public static final Holder<Attribute> WATER_MOVEMENT_EFFICIENCY = attribute("water_movement_efficiency", 0.0);
    public static final Holder<Attribute> WAYPOINT_TRANSMIT_RANGE =
            attribute("waypoint_transmit_range", 0.0, Attribute.Sentiment.NEUTRAL);
    public static final Holder<Attribute> WAYPOINT_RECEIVE_RANGE =
            attribute("waypoint_receive_range", 0.0, Attribute.Sentiment.NEUTRAL);

    /** A player's own attack damage, under a weapon's (the game's {@code Player.createAttributes}). */
    public static final double PLAYER_ATTACK_DAMAGE = 1.0;

    /** An attribute by its id as the game saves it: the game's above, else one of a mod's or the game's Kiln lacks. */
    public static final Codec<Holder<Attribute>> CODEC = Identifier.CODEC.xmap(Attributes::byId,
            h -> h.unwrapKey().orElseThrow().identifier());

    private static Holder<Attribute> attribute(String name, double defaultValue) {
        return attribute(name, defaultValue, Attribute.Sentiment.POSITIVE);
    }

    private static Holder<Attribute> attribute(String name, double defaultValue, Attribute.Sentiment sentiment) {
        Identifier id = Identifier.withDefaultNamespace(name);
        Holder<Attribute> holder = Holder.Reference.kiln$of(ResourceKey.create(Registries.ATTRIBUTE, id),
                new Attribute("attribute.name." + name, defaultValue).setSentiment(sentiment));
        BY_ID.put(id, holder);
        return holder;
    }

    /** The attribute called {@code id}; one Kiln doesn't name is made once, named {@code attribute.name.<path>}. */
    public static Holder<Attribute> byId(Identifier id) {
        return BY_ID.computeIfAbsent(id, i -> Holder.Reference.kiln$of(ResourceKey.create(Registries.ATTRIBUTE, i),
                new Attribute("attribute.name." + i.getPath(), 0.0)));
    }
}
