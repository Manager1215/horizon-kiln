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

    public static Vec3 atLowerCornerOf(Vec3i v) {
        return new Vec3(v.getX(), v.getY(), v.getZ());
    }

    public static Vec3 atBottomCenterOf(Vec3i v) {
        return new Vec3(v.getX() + 0.5, v.getY(), v.getZ() + 0.5);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 add(Vec3 v) {
        return add(v.x, v.y, v.z);
    }

    public Vec3 subtract(double dx, double dy, double dz) {
        return add(-dx, -dy, -dz);
    }

    public Vec3 subtract(Vec3 v) {
        return subtract(v.x, v.y, v.z);
    }

    public Vec3 scale(double factor) {
        return multiply(factor, factor, factor);
    }

    public Vec3 multiply(double fx, double fy, double fz) {
        return new Vec3(x * fx, y * fy, z * fz);
    }

    public Vec3 reverse() {
        return scale(-1.0);
    }

    public double dot(Vec3 v) {
        return x * v.x + y * v.y + z * v.z;
    }

    public Vec3 cross(Vec3 v) {
        return new Vec3(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSqr());
    }

    public double horizontalDistance() {
        return Math.sqrt(x * x + z * z);
    }

    public double horizontalDistanceSqr() {
        return x * x + z * z;
    }

    /** The same direction, length 1; zero stays zero. */
    public Vec3 normalize() {
        double length = length();
        return length < 1.0E-5 ? ZERO : new Vec3(x / length, y / length, z / length);
    }

    public double distanceToSqr(double ox, double oy, double oz) {
        double dx = ox - x;
        double dy = oy - y;
        double dz = oz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceToSqr(Vec3 v) {
        return distanceToSqr(v.x, v.y, v.z);
    }

    public double distanceTo(Vec3 v) {
        return Math.sqrt(distanceToSqr(v));
    }

    public Vec3 lerp(Vec3 to, double delta) {
        return new Vec3(x + (to.x - x) * delta, y + (to.y - y) * delta, z + (to.z - z) * delta);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Vec3 v && Double.compare(v.x, x) == 0 && Double.compare(v.y, y) == 0
                && Double.compare(v.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
