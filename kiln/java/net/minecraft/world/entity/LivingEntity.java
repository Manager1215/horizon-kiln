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
    /** What it wears (armour, a body slot), by slot; the hands are above. */
    protected final Map<EquipmentSlot, ItemStack> worn = new java.util.EnumMap<>(EquipmentSlot.class);
    /** Its effects, by effect, as the game said. */
    protected final Map<Holder<net.minecraft.world.effect.MobEffect>, net.minecraft.world.effect.MobEffectInstance>
            activeEffects = new java.util.LinkedHashMap<>();

    public java.util.Collection<net.minecraft.world.effect.MobEffectInstance> getActiveEffects() {
        return activeEffects.values();
    }

    public Map<Holder<net.minecraft.world.effect.MobEffect>, net.minecraft.world.effect.MobEffectInstance> getActiveEffectsMap() {
        return activeEffects;
    }

    public boolean hasEffect(Holder<net.minecraft.world.effect.MobEffect> effect) {
        return getEffect(effect) != null;
    }

    public net.minecraft.world.effect.MobEffectInstance getEffect(Holder<net.minecraft.world.effect.MobEffect> effect) {
        net.minecraft.world.effect.MobEffectInstance found = activeEffects.get(effect);
        if (found == null) {
            for (net.minecraft.world.effect.MobEffectInstance e : activeEffects.values()) {
                if (e.is(effect)) {
                    return e;
                }
            }
        }
        return found;
    }

    /** Whether all {@code effects} are ambient (a beacon's), as vanilla's particles ask. */
    public static boolean areAllEffectsAmbient(java.util.Collection<net.minecraft.world.effect.MobEffectInstance> effects) {
        for (net.minecraft.world.effect.MobEffectInstance e : effects) {
            if (e.isVisible() && !e.isAmbient()) {
                return false;
            }
        }
        return true;
    }

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

    /** Armour points, as the game said for the window's player (its worn armour's own); 0 otherwise. */
    protected double armorValue;

    public int getArmorValue() {
        return (int) Math.floor(armorValue);
    }

    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> mainHand;
            case OFFHAND -> offHand;
            default -> worn.getOrDefault(slot, ItemStack.EMPTY);
        };
    }

    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot) {
            case MAINHAND -> mainHand = stack;
            case OFFHAND -> offHand = stack;
            default -> worn.put(slot, stack);
        }
    }

    /** Kiln's own: an instance of {@code attribute} whose new base becomes an effect. */
    protected AttributeInstance kiln$newAttribute(Holder<Attribute> attribute, double base, double value) {
        return new AttributeInstance(attribute, base, value, i -> i.getAttribute().unwrapKey()
                .ifPresent(k -> kiln$effect("attribute " + k.identifier() + " " + i.getBaseValue())));
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public boolean addEffect(net.minecraft.world.effect.MobEffectInstance p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.addEffect"); return false; }
    public boolean removeEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.removeEffect"); return false; }
    public boolean hurtServer(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.damagesource.DamageSource p1, float p2) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.hurtServer"); return false; }
    public void setJumping(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setJumping"); }
    // End of what tools/kiln-stubs.py generated.
}
