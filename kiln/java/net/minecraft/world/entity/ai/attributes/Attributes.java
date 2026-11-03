package net.minecraft.world.entity.ai.attributes;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Kiln's stand-in for {@code Attributes}: the game's attributes, by id, with vanilla's defaults. */
public class Attributes {
    public static final Holder<Attribute> ARMOR = attribute("armor", 0.0);
    public static final Holder<Attribute> ARMOR_TOUGHNESS = attribute("armor_toughness", 0.0);
    public static final Holder<Attribute> ATTACK_DAMAGE = attribute("attack_damage", 2.0);
    public static final Holder<Attribute> ATTACK_KNOCKBACK = attribute("attack_knockback", 0.0);
    public static final Holder<Attribute> ATTACK_SPEED = attribute("attack_speed", 4.0);
    public static final Holder<Attribute> BLOCK_BREAK_SPEED = attribute("block_break_speed", 1.0);
    public static final Holder<Attribute> BLOCK_INTERACTION_RANGE = attribute("block_interaction_range", 4.5);
    public static final Holder<Attribute> ENTITY_INTERACTION_RANGE = attribute("entity_interaction_range", 3.0);
    public static final Holder<Attribute> FALL_DAMAGE_MULTIPLIER = attribute("fall_damage_multiplier", 1.0);
    public static final Holder<Attribute> GRAVITY = attribute("gravity", 0.08);
    public static final Holder<Attribute> JUMP_STRENGTH = attribute("jump_strength", 0.42);
    public static final Holder<Attribute> KNOCKBACK_RESISTANCE = attribute("knockback_resistance", 0.0);
    public static final Holder<Attribute> LUCK = attribute("luck", 0.0);
    public static final Holder<Attribute> MAX_ABSORPTION = attribute("max_absorption", 0.0);
    public static final Holder<Attribute> MAX_HEALTH = attribute("max_health", 20.0);
    public static final Holder<Attribute> MOVEMENT_SPEED = attribute("movement_speed", 0.7);
    public static final Holder<Attribute> SAFE_FALL_DISTANCE = attribute("safe_fall_distance", 3.0);
    public static final Holder<Attribute> SCALE = attribute("scale", 1.0);
    public static final Holder<Attribute> STEP_HEIGHT = attribute("step_height", 0.6);

    private static Holder<Attribute> attribute(String name, double defaultValue) {
        Identifier id = Identifier.withDefaultNamespace(name);
        return Holder.Reference.kiln$of(ResourceKey.create(Registries.ATTRIBUTE, id),
                new Attribute("attribute.name." + name, defaultValue));
    }
}
