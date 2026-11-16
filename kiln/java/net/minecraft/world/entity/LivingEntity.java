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

    /** Ticks after a mod's impulse (a launch) when a fall doesn't hurt: kept in Java; Horizon's fall damage is its own. */
    protected int postImpulseGraceTime;

    public void applyPostImpulseGraceTime(int ticks) {
        postImpulseGraceTime = Math.max(postImpulseGraceTime, ticks);
    }

    /**
     * A living entity's tick: {@link Entity#tick}'s, for the window's player (D-093), the server's players, and the
     * server's other living entities as Horizon ticks them (Kiln's Mixin bridge's {@code entity_tick} row, D-145).
     */
    @Override
    public void tick() {
        kiln.Bridge.body(null);
        super.tick();
    }

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

    /** Its scale attribute: Kiln keeps none, so its size is its type's (D-173). */
    public float getScale() {
        return 1.0F;
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

    /** Swings the arm: Horizon swings the player's when a use takes the click; nothing more here. */
    public boolean swing(InteractionHand hand, net.minecraft.world.item.component.SwingAnimation animation,
            boolean updateSelf) {
        return true;
    }

    public void swing(InteractionHand hand) {
        swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
    }

    public void swing(InteractionHand hand, boolean updateSelf) {
        swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, updateSelf);
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

    /** Gliding with an elytra, as the game said for the window's player; false otherwise. */
    protected boolean fallFlying;

    public boolean isFallFlying() {
        return fallFlying;
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

    /**
     * The effect taken off, as milk or a mod takes one: whether it had it. The window's player's goes to Horizon
     * ({@code LocalPlayer}); another entity's stays in Java for now.
     */
    public boolean removeEffect(Holder<net.minecraft.world.effect.MobEffect> effect) {
        boolean had = activeEffects.remove(effect) != null;
        if (had) {
            kiln.Missing.called("an effect taken off an entity on the server");
        }
        return had;
    }

    /** Kiln's own: an instance of {@code attribute} whose new base becomes an effect. */
    protected AttributeInstance kiln$newAttribute(Holder<Attribute> attribute, double base, double value) {
        return new AttributeInstance(attribute, base, value, i -> i.getAttribute().unwrapKey()
                .ifPresent(k -> kiln$effect("attribute " + k.identifier() + " " + i.getBaseValue())));
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public boolean addEffect(net.minecraft.world.effect.MobEffectInstance p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.addEffect"); return false; }
    /**
     * The entity died on the server, of {@code source}: Horizon kills it; the mods' mixins in this run at Horizon's
     * {@code mob_killed} hook for a mob a player killed (Kiln's Mixin bridge, D-106).
     */
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        kiln.Bridge.body(null, source);
    }

    /**
     * The entity hurt by {@code amount} on the server: Horizon hurts it; the mods' mixins in this run at Horizon's
     * {@code living_incoming_damage} hook, where a mixin that cancels it at its head cancels the hit and one that
     * changes the amount changes the damage (Kiln's Mixin bridge, D-102). Hurt: true, as the game's when it is.
     */
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return kiln.Bridge.body(Boolean.TRUE, level, source, amount);
    }
    public void setJumping(boolean p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setJumping"); }
    // End of what tools/kiln-stubs.py generated.
}
