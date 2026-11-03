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
}
