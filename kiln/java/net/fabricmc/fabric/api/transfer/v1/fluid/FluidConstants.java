package net.fabricmc.fabric.api.transfer.v1.fluid;

/** Kiln's stand-in for Fabric's {@code FluidConstants}: fluid amounts in droplets, 81000 to a bucket, as Fabric's. */
public final class FluidConstants {
    public static final long BUCKET = 81000;
    public static final long BOTTLE = 27000;
    public static final long BLOCK = 81000;
    public static final long INGOT = 9000;
    public static final long NUGGET = 1000;
    public static final long DROPLET = 1;

    private FluidConstants() {}
}
