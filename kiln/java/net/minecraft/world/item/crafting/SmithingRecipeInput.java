package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code SmithingRecipeInput}: a smithing table's template, base and addition. */
public record SmithingRecipeInput(ItemStack template, ItemStack base, ItemStack addition) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> template;
            case 1 -> base;
            case 2 -> addition;
            default -> throw new IllegalArgumentException("Recipe does not contain slot " + index);
        };
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public boolean isEmpty() {
        return template.isEmpty() && base.isEmpty() && addition.isEmpty();
    }
}
