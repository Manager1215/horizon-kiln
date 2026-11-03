package net.minecraft.world.phys;

import net.minecraft.core.Vec3i;

/** Kiln's stand-in for {@code Vec3}: a point or a motion. */
public class Vec3 {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3i v) {
        this(v.getX(), v.getY(), v.getZ());
    }

    public static Vec3 atCenterOf(Vec3i v) {
        return new Vec3(v.getX() + 0.5, v.getY() + 0.5, v.getZ() + 0.5);
    }

    public final double x() {
        return x;
    }

    public final double y() {
        return y;
    }

    public final double z() {
        return z;
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
