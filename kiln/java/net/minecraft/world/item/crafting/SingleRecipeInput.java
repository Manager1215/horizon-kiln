package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code SingleRecipeInput}: one item, as a furnace or a stonecutter has. */
public record SingleRecipeInput(ItemStack item) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        if (index != 0) {
            throw new IllegalArgumentException("No item for index " + index);
        }
        return item;
    }

    @Override
    public int size() {
        return 1;
    }
}
