package net.minecraft.world.item;

/**
 * Kiln's stand-in for {@code FireworkRocketItem}: the rocket's flight durations a crafting recipe makes (one to three
 * gunpowder). Horizon flies the rockets; a mod's subclass is an item as any other here.
 */
public class FireworkRocketItem extends Item {
    public static final byte[] CRAFTABLE_DURATIONS = {1, 2, 3};
    public static final double ROCKET_PLACEMENT_OFFSET = 0.15;

    public FireworkRocketItem(Properties properties) {
        super(properties);
    }
}
