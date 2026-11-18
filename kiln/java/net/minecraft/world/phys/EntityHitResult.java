package net.minecraft.world.phys;

import net.minecraft.world.entity.Entity;

/** Kiln's stand-in for {@code EntityHitResult}: the entity a look or a shot met, and where. */
public class EntityHitResult extends HitResult {
    private final Entity entity;

    public EntityHitResult(Entity entity) {
        this(entity, new Vec3(entity.getX(), entity.getY(), entity.getZ()));
    }

    public EntityHitResult(Entity entity, Vec3 location) {
        super(location);
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }

    @Override
    public HitResult.Type getType() {
        return HitResult.Type.ENTITY;
    }
}
