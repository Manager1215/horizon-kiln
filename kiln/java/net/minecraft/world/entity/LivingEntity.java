package net.minecraft.world.entity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code LivingEntity}: health, attributes and what the entity holds. */
public abstract class LivingEntity extends Entity {
    protected float health = 20.0F;
    protected final Map<Holder<Attribute>, AttributeInstance> attributes = new HashMap<>();
    protected ItemStack mainHand = ItemStack.EMPTY;
    protected ItemStack offHand = ItemStack.EMPTY;

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        float clamped = Math.max(0.0F, Math.min(health, getMaxHealth()));
        if (clamped != this.health) {
            this.health = clamped;
            kiln$effect("health " + clamped);
        }
    }

    public void heal(float amount) {
        if (health > 0.0F) {
            setHealth(health + amount);
        }
    }

    public final float getMaxHealth() {
        return (float) getAttributeValue(Attributes.MAX_HEALTH);
    }

    /** The entity's instance of {@code attribute}: as the game said, else at the attribute's default. */
    public AttributeInstance getAttribute(Holder<Attribute> attribute) {
        return attributes.computeIfAbsent(attribute, a -> kiln$newAttribute(a, a.value().getDefaultValue(), a.value().getDefaultValue()));
    }

    public double getAttributeValue(Holder<Attribute> attribute) {
        return getAttribute(attribute).getValue();
    }

    public double getAttributeBaseValue(Holder<Attribute> attribute) {
        return getAttribute(attribute).getBaseValue();
    }

    public ItemStack getItemInHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHand : offHand;
    }

    public void setItemInHand(InteractionHand hand, ItemStack stack) {
        if (hand == InteractionHand.MAIN_HAND) {
            mainHand = stack;
        } else {
            offHand = stack;
        }
    }

    public ItemStack getMainHandItem() {
        return mainHand;
    }

    public ItemStack getOffhandItem() {
        return offHand;
    }

    /** Kiln's own: an instance of {@code attribute} whose new base becomes an effect. */
    protected AttributeInstance kiln$newAttribute(Holder<Attribute> attribute, double base, double value) {
        return new AttributeInstance(attribute, base, value, i -> i.getAttribute().unwrapKey()
                .ifPresent(k -> kiln$effect("attribute " + k.identifier() + " " + i.getBaseValue())));
    }
}
