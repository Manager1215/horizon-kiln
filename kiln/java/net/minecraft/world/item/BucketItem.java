package net.minecraft.world.item;

import net.minecraft.world.level.material.Fluid;

/**
 * Kiln's stand-in for {@code BucketItem}: an item that holds a block of one fluid. Horizon empties and fills the game's
 * buckets in the world; Kiln knows which fluid each holds, so a mod's tank takes it (Fabric's {@code FluidStorage}).
 */
public class BucketItem extends Item {
    private final Fluid content;

    public BucketItem(Fluid content, Item.Properties properties) {
        super(properties);
        this.content = content;
    }

    /** Kiln's own: the fluid it holds (Fabric reads vanilla's private field). */
    public Fluid kiln$content() {
        return content;
    }
}
