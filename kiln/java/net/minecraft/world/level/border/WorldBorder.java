package net.minecraft.world.level.border;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Kiln's stand-in for {@code WorldBorder}: the world's edge, as big as vanilla's default (60 million blocks across).
 * Horizon keeps the world's own border and checks it itself; a mod's code that asks before it changes a block finds
 * every place it can reach inside.
 */
public class WorldBorder extends SavedData {
    /** Kiln's own: the border a mod's code reads from a level. */
    public static final WorldBorder KILN$OPEN = new WorldBorder();
    private static final double SIZE = 5.9999968E7;

    public WorldBorder() {}

    public double getSize() {
        return SIZE;
    }

    public double getCenterX() {
        return 0.0;
    }

    public double getCenterZ() {
        return 0.0;
    }

    public boolean isWithinBounds(double x, double z) {
        return Math.abs(x) < SIZE / 2 && Math.abs(z) < SIZE / 2;
    }

    public boolean isWithinBounds(BlockPos pos) {
        return isWithinBounds(pos.getX(), pos.getZ());
    }
}
