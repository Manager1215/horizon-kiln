package net.minecraft.world.level;

import net.minecraft.world.item.Item;

/** Kiln's stand-in for {@code ItemLike}: anything that is, or has, an item. */
public interface ItemLike {
    Item asItem();
}
