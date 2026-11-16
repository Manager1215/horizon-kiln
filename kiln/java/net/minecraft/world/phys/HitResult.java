package net.minecraft.world.phys;

/** Kiln's stand-in for {@code HitResult}: where a look or a shot met something. */
public abstract class HitResult {
    protected final Vec3 location;

    protected HitResult(Vec3 location) {
        this.location = location;
    }

    public Vec3 getLocation() {
        return location;
    }

    /** What was met: nothing, a block or an entity. */
    public abstract Type getType();

    /** The square of the distance from {@code entity}'s position to where it was met. */
    public double distanceTo(net.minecraft.world.entity.Entity entity) {
        double dx = location.x - entity.getX(), dy = location.y - entity.getY(), dz = location.z - entity.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static enum Type {
        MISS, BLOCK, ENTITY;
    }
}
