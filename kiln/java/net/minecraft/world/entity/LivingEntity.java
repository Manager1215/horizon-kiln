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
public abstract class LivingEntity extends Entity implements Attackable {
    protected float health = 20.0F;

    public LivingEntity() {}

    public LivingEntity(EntityType<? extends LivingEntity> type, net.minecraft.world.level.Level level) {
        super(type, level);
    }
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
        // As vanilla's: the entity's movement and AI step in its tick (D-200), Horizon's own; the mods' mixins in it run.
        aiStep();
    }

    /** The entity's movement and AI, in its tick: Horizon moves it, so the body is empty. */
    public void aiStep() {}

    /**
     * A jump off the ground: Horizon jumps the entity, so its body is the bridge's ({@code living_jump}, D-200). When a
     * mod calls it, nothing moves.
     */
    protected void jumpFromGround() {
        if (kiln.Bridge.calling("jumpFromGround")) {
            kiln.Bridge.body(null);
        }
    }

    /**
     * A fall's damage, as the entity lands: Horizon deals it, so the body is the bridge's ({@code living_fall}, D-200):
     * the distance and multiplier as the mods' mixins left them go back. When a mod calls it, nothing is dealt.
     */
    public boolean causeFallDamage(double fallDistance, float damageMultiplier,
            net.minecraft.world.damagesource.DamageSource damageSource) {
        if (kiln.Bridge.calling("causeFallDamage")) {
            kiln.Bridge.body(null, fallDistance, damageMultiplier, damageSource);
        }
        return false;
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

    private net.minecraft.world.damagesource.CombatTracker combatTracker;

    /** What hurt it lately, for its death message (on the client, as the game's there: none, D-218). */
    public net.minecraft.world.damagesource.CombatTracker getCombatTracker() {
        if (combatTracker == null) {
            combatTracker = new net.minecraft.world.damagesource.CombatTracker(this);
        }
        return combatTracker;
    }

    /** Dead or dying: no health left, as the game's (DeathLogPlus watches the player's, D-218). */
    public boolean isDeadOrDying() {
        return getHealth() <= 0.0F;
    }

    /** Alive while not removed and with health left, as the game's. */
    @Override
    public boolean isAlive() {
        return !isRemoved() && getHealth() > 0.0F;
    }

    public void setHealth(float health) {
        float clamped = Math.max(0.0F, Math.min(health, getMaxHealth()));
        if (clamped != this.health) {
            this.health = clamped;
            kiln$effect("health " + clamped);
        }
    }

    public void heal(float amount) {
        if (kiln.Bridge.calling("heal")) {
            // Horizon's heal ({@code living_heal}, D-200): the amount as the mods' mixins left it goes back, Horizon heals.
            kiln.Bridge.body(null, amount);
            return;
        }
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

    /**
     * Taking an item or orb from the ground: the pickup animation, which Horizon plays. Nothing here; the pickup
     * stand-ins call it where vanilla's {@code playerTouch} does, so a mixin at it finds its place (D-213).
     */
    public void take(net.minecraft.world.entity.Entity entity, int count) {
    }

    /** An item was picked up: Horizon's (the advancement trigger, the item's thrower). Nothing here (D-213). */
    public void onItemPickup(net.minecraft.world.entity.item.ItemEntity item) {
    }

    /** The head's yaw; the window's player's is its yaw, as the game turns its head with the camera. */
    @Override
    public float getYHeadRot() {
        return yHeadRot;
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
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final net.minecraft.network.syncher.EntityDataAccessor<java.lang.Float> DATA_HEALTH_ID = new net.minecraft.network.syncher.EntityDataAccessor<>((int) 0, (net.minecraft.network.syncher.EntityDataSerializer) null);
    private static final net.minecraft.network.syncher.EntityDataAccessor<java.util.List<net.minecraft.core.particles.ParticleOptions>> DATA_EFFECT_PARTICLES = new net.minecraft.network.syncher.EntityDataAccessor<>((int) 0, (net.minecraft.network.syncher.EntityDataSerializer) null);
    public java.util.Map<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack> lastEquipmentItems;
    public net.minecraft.world.entity.LivingEntity.SwingState swingState;
    public int hurtTime;
    public int hurtDuration;
    public int deathTime;
    protected int attackStrengthTicker;
    public net.minecraft.world.entity.WalkAnimationState walkAnimation;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    public net.minecraft.world.entity.ElytraAnimationState elytraAnimationState;
    public boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    public double lerpYHeadRot;
    public int lerpHeadSteps;
    public int fallFlyTicks;
    private net.minecraft.core.BlockPos lastPos;
    public net.minecraft.world.entity.ai.Brain<? extends net.minecraft.world.entity.LivingEntity> getBrain() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getBrain"); return null; }
    public void kill(net.minecraft.server.level.ServerLevel p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.kill"); }
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createLivingAttributes() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.createLivingAttributes"); return new net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder(); }
    public float getSwimAmount(float p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getSwimAmount"); return 0.0F; }
    public void baseTick() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.baseTick"); }
    public boolean isBaby() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isBaby"); return false; }
    public float getAgeScale() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getAgeScale"); return 0.0F; }
    public boolean shouldDropLoot(net.minecraft.server.level.ServerLevel p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.shouldDropLoot"); return false; }
    public net.minecraft.world.entity.LivingEntity getLastHurtByMob() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getLastHurtByMob"); return this; }
    public net.minecraft.world.entity.LivingEntity getLastAttacker() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getLastAttacker"); return this; }
    public int getLastHurtByMobTimestamp() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getLastHurtByMobTimestamp"); return 0; }
    public void setLastHurtByMob(net.minecraft.world.entity.LivingEntity p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setLastHurtByMob"); }
    public net.minecraft.world.entity.LivingEntity getLastHurtMob() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getLastHurtMob"); return this; }
    public int getLastHurtMobTimestamp() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getLastHurtMobTimestamp"); return 0; }
    public void setLastHurtMob(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setLastHurtMob"); }
    public int getNoActionTime() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getNoActionTime"); return 0; }
    public void setNoActionTime(int p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setNoActionTime"); }
    public void onEquipItem(net.minecraft.world.entity.EquipmentSlot p0, net.minecraft.world.item.ItemStack p1, net.minecraft.world.item.ItemStack p2) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.onEquipItem"); }
    public void remove(net.minecraft.world.entity.Entity.RemovalReason p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.remove"); }
    public net.minecraft.world.entity.item.ItemEntity drop(net.minecraft.world.item.ItemStack p0, boolean p1, net.minecraft.util.Prediction p2) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.drop"); return null; }
    public double getVisibilityPercent(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.entity.Entity p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getVisibilityPercent"); return 0.0; }
    public boolean canBeSeenAsEnemy() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.canBeSeenAsEnemy"); return false; }
    public boolean canBeSeenByAnyone() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.canBeSeenByAnyone"); return false; }
    public boolean removeAllEffects() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.removeAllEffects"); return false; }
    public float getEffectBlendFactor(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> p0, float p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getEffectBlendFactor"); return 0.0F; }
    public boolean addEffect(net.minecraft.world.effect.MobEffectInstance p0, net.minecraft.world.entity.Entity p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.addEffect"); return false; }
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.canBeAffected"); return false; }
    public void forceAddEffect(net.minecraft.world.effect.MobEffectInstance p0, net.minecraft.world.entity.Entity p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.forceAddEffect"); }
    private void refreshDirtyAttributes() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.refreshDirtyAttributes"); }
    public void dealDefaultKnockback(net.minecraft.world.damagesource.DamageSource p0, float p1, boolean p2) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.dealDefaultKnockback"); }
    private boolean checkTotemDeathProtection(net.minecraft.world.damagesource.DamageSource p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.checkTotemDeathProtection"); return false; }
    public net.minecraft.world.damagesource.DamageSource getLastDamageSource() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getLastDamageSource"); return null; }
    public void makeSound(net.minecraft.sounds.SoundEvent p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.makeSound"); }
    protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.damagesource.DamageSource p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.dropAllDeathLoot"); }
    protected void dropEquipment(net.minecraft.server.level.ServerLevel p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.dropEquipment"); }
    public void knockback(double p0, double p1, double p2, net.minecraft.world.damagesource.DamageSource p3, float p4, boolean p5) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.knockback"); }
    public void knockback(double p0, double p1, double p2, net.minecraft.world.damagesource.DamageSource p3, float p4) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.knockback"); }
    public float getHurtDir() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getHurtDir"); return 0.0F; }
    public void postPiercingAttack() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.postPiercingAttack"); }
    public java.util.Optional<net.minecraft.core.BlockPos> getLastClimbablePos() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getLastClimbablePos"); return java.util.Optional.empty(); }
    public boolean onClimbable() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.onClimbable"); return false; }
    public void hurtArmor(net.minecraft.world.damagesource.DamageSource p0, float p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.hurtArmor"); }
    public void hurtHelmet(net.minecraft.world.damagesource.DamageSource p0, float p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.hurtHelmet"); }
    public net.minecraft.world.entity.LivingEntity getKillCredit() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getKillCredit"); return this; }
    public float getMaxAbsorption() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getMaxAbsorption"); return 0.0F; }
    public int getStingerCount() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getStingerCount"); return 0; }
    public void setStingerCount(int p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setStingerCount"); }
    public net.minecraft.world.entity.ai.attributes.AttributeMap getAttributes() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getAttributes"); return null; }
    public net.minecraft.world.item.ItemStack getItemHeldByArm(net.minecraft.world.entity.HumanoidArm p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getItemHeldByArm"); return null; }
    public net.minecraft.world.item.ItemStack getWeaponItem() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getWeaponItem"); return null; }
    public net.minecraft.world.item.component.AttackRange getAttackRangeWith(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getAttackRangeWith"); return new net.minecraft.world.item.component.AttackRange((float) 0.0F, (float) 0.0F, (float) 0.0F, (float) 0.0F, (float) 0.0F, (float) 0.0F); }
    public net.minecraft.world.item.ItemStack getActiveItem() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getActiveItem"); return null; }
    public boolean isHolding(net.minecraft.world.item.Item p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isHolding"); return false; }
    public boolean isHolding(java.util.function.Predicate<net.minecraft.world.item.ItemStack> p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isHolding"); return false; }
    public boolean hasItemInSlot(net.minecraft.world.entity.EquipmentSlot p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.hasItemInSlot"); return false; }
    public float getVoicePitch() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getVoicePitch"); return 0.0F; }
    protected boolean isImmobile() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isImmobile"); return false; }
    public void push(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.push"); }
    public boolean shouldShowName() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.shouldShowName"); return false; }
    public float getJumpBoostPower() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getJumpBoostPower"); return 0.0F; }
    protected float getWaterSlowDown() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getWaterSlowDown"); return 0.0F; }
    public void travel(net.minecraft.world.phys.Vec3 p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.travel"); }
    public void stopFallFlying() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.stopFallFlying"); }
    public net.minecraft.world.phys.Vec3 getFluidFallingAdjustedMovement(double p0, boolean p1, net.minecraft.world.phys.Vec3 p2) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getFluidFallingAdjustedMovement"); return null; }
    public float getSpeed() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getSpeed"); return 0.0F; }
    protected void tickHeadTurn(float p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.tickHeadTurn"); }
    protected void doPush(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.doPush"); }
    public boolean isAutoSpinAttack() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isAutoSpinAttack"); return false; }
    public void stopRiding() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.stopRiding"); }
    public boolean hasLineOfSight(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.hasLineOfSight"); return false; }
    public net.minecraft.world.entity.LivingEntity.SwingDescription getCurrentSwing() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getCurrentSwing"); return null; }
    public float getSwingAnimation(float p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getSwingAnimation"); return 0.0F; }
    public boolean isSwinging() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isSwinging"); return false; }
    public boolean isPushable() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isPushable"); return false; }
    public void setYHeadRot(float p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setYHeadRot"); }
    public void setYBodyRot(float p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setYBodyRot"); }
    public float getAbsorptionAmount() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getAbsorptionAmount"); return 0.0F; }
    public void setAbsorptionAmount(float p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setAbsorptionAmount"); }
    public net.minecraft.world.entity.HumanoidArm getMainArm() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getMainArm"); return null; }
    public boolean isUsingItem() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isUsingItem"); return false; }
    public net.minecraft.world.InteractionHand getUsedItemHand() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getUsedItemHand"); return null; }
    public net.minecraft.world.entity.item.ItemEntity createItemStackToDrop(net.minecraft.world.item.ItemStack p0, boolean p1, boolean p2) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.createItemStackToDrop"); return null; }
    public void startUsingItem(net.minecraft.world.InteractionHand p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.startUsingItem"); }
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.onSyncedDataUpdated"); }
    public void lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor p0, net.minecraft.world.phys.Vec3 p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.lookAt"); }
    public float getPreciseBodyRotation(float p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getPreciseBodyRotation"); return 0.0F; }
    protected void completeUsingItem() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.completeUsingItem"); }
    public net.minecraft.world.item.ItemStack getUseItem() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getUseItem"); return null; }
    public int getUseItemRemainingTicks() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getUseItemRemainingTicks"); return 0; }
    public int getTicksUsingItem() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getTicksUsingItem"); return 0; }
    public void releaseUsingItem() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.releaseUsingItem"); }
    public void stopUsingItem() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.stopUsingItem"); }
    public boolean isBlocking() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isBlocking"); return false; }
    public net.minecraft.world.item.ItemStack getItemBlockingWith() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getItemBlockingWith"); return null; }
    public boolean isSuppressingSlidingDownLadder() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isSuppressingSlidingDownLadder"); return false; }
    public boolean isVisuallySwimming() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isVisuallySwimming"); return false; }
    public int getFallFlyingTicks() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getFallFlyingTicks"); return 0; }
    public boolean randomTeleport(double p0, double p1, double p2, boolean p3, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> p4) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.randomTeleport"); return false; }
    public boolean isAffectedByPotions() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isAffectedByPotions"); return false; }
    protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getDefaultDimensions"); return null; }
    public com.google.common.collect.ImmutableList<net.minecraft.world.entity.Pose> getDismountPoses() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getDismountPoses"); return null; }
    public net.minecraft.world.phys.AABB getLocalBoundsForPose(net.minecraft.world.entity.Pose p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getLocalBoundsForPose"); return null; }
    public void setSleepingPos(net.minecraft.core.BlockPos p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.setSleepingPos"); }
    public boolean isSleeping() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isSleeping"); return false; }
    public net.minecraft.core.Direction getBedOrientation() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getBedOrientation"); return null; }
    public void onEquippedItemBroken(net.minecraft.world.item.ItemStack p0, net.minecraft.world.entity.EquipmentSlot p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.onEquippedItemBroken"); }
    public net.minecraft.world.entity.EquipmentSlot getEquipmentSlotForItem(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.getEquipmentSlotForItem"); return null; }
    public boolean isEquippableInSlot(net.minecraft.world.item.ItemStack p0, net.minecraft.world.entity.EquipmentSlot p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isEquippableInSlot"); return false; }
    public boolean canFreeze() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.canFreeze"); return false; }
    public boolean isCurrentlyGlowing() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isCurrentlyGlowing"); return false; }
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket p0) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.recreateFromPacket"); }
    public float maxUpStep() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.maxUpStep"); return 0.0F; }
    public void lerpHeadRotationStep(int p0, double p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.lerpHeadRotationStep"); }
    public boolean hasInfiniteMaterials() { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.hasInfiniteMaterials"); return false; }
    public boolean isInvulnerableTo(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.damagesource.DamageSource p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.isInvulnerableTo"); return false; }
    public static boolean canGlideUsing(net.minecraft.world.item.ItemStack p0, net.minecraft.world.entity.EquipmentSlot p1) { kiln.Missing.called("net.minecraft.world.entity.LivingEntity.canGlideUsing"); return false; }
    public static class SwingState {
        public int ticks;
        public SwingState() {  }
    }
    public static record SwingDescription(net.minecraft.world.InteractionHand hand, net.minecraft.world.item.component.SwingAnimation animation, int durationTicks) {
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
