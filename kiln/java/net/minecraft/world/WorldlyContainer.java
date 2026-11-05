package net.minecraft.world;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code WorldlyContainer}: a container whose faces reach some of its slots. */
public interface WorldlyContainer extends Container {
    int[] getSlotsForFace(Direction face);

    boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face);

    boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face);
}
