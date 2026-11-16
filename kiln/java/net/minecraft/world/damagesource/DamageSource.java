package net.minecraft.world.damagesource;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Kiln's stand-in for {@code DamageSource}: a hit's damage type, the entity that caused it and the one that dealt it,
 * as Horizon said (Kiln's Mixin bridge, D-102). Its type's tags aren't Kiln's yet: {@code is(TagKey)} says no.
 */
public class DamageSource {
    private final Holder<DamageType> type;
    private final Entity causing;
    private final Entity direct;
    private final Vec3 position;

    public DamageSource(Holder<DamageType> type, Entity direct, Entity causing) {
        this(type, direct, causing, null);
    }

    public DamageSource(Holder<DamageType> type, Vec3 position) {
        this(type, null, null, position);
    }

    public DamageSource(Holder<DamageType> type, Entity entity) {
        this(type, entity, entity, null);
    }

    public DamageSource(Holder<DamageType> type) {
        this(type, null, null, null);
    }

    protected DamageSource() {
        this(null, null, null, null);
    }

    private DamageSource(Holder<DamageType> type, Entity direct, Entity causing, Vec3 position) {
        this.type = type;
        this.direct = direct;
        this.causing = causing;
        this.position = position;
    }

    /** The entity the damage comes from in the end (an arrow's shooter). */
    public Entity getEntity() {
        return causing;
    }

    /** The entity that dealt it (the arrow). */
    public Entity getDirectEntity() {
        return direct;
    }

    public boolean isDirect() {
        return causing == direct;
    }

    public Vec3 getSourcePosition() {
        return position != null ? position : direct != null ? direct.position() : null;
    }

    public Holder<DamageType> typeHolder() {
        return type;
    }

    public DamageType type() {
        return type == null ? null : type.value();
    }

    public String getMsgId() {
        DamageType t = type();
        return t == null ? "generic" : t.msgId();
    }

    /** Whether it is of the damage type {@code key} ({@code DamageTypes.FALL}). */
    public boolean is(ResourceKey<DamageType> key) {
        return type != null && type.is(key);
    }

    /** Whether its type is in the tag: Kiln has no damage type tags yet, so no. */
    public boolean is(TagKey<DamageType> tag) {
        kiln.Missing.called("net.minecraft.world.damagesource.DamageSource.is(TagKey)");
        return false;
    }

    public boolean isCreativePlayer() {
        return causing instanceof net.minecraft.world.entity.player.Player p && p.isCreative();
    }

    @Override
    public String toString() {
        return "DamageSource (" + getMsgId() + ")";
    }
}
