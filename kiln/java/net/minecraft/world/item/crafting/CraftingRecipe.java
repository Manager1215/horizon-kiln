package net.minecraft.world.item.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code CraftingRecipe}: a crafting table's (or the inventory's grid's) recipe. */
public interface CraftingRecipe extends Recipe<CraftingInput> {
    @Override
    default RecipeType<CraftingRecipe> getType() {
        return RecipeType.CRAFTING;
    }

    RecipeSerializer<? extends CraftingRecipe> getSerializer();

    CraftingBookCategory category();

    /** What stays in the grid: Kiln doesn't know items' remainders yet (a bucket's), so nothing. */
    default NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    record CraftingBookInfo(CraftingBookCategory category, String group) implements Recipe.BookInfo<CraftingBookCategory> {}
}
