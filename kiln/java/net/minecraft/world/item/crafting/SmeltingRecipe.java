package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStackTemplate;

/** Kiln's stand-in for {@code SmeltingRecipe}: a furnace's recipe. */
public class SmeltingRecipe extends AbstractCookingRecipe {
    public SmeltingRecipe(CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient input, ItemStackTemplate result,
            float experience, int cookingTime) {
        super(commonInfo, bookInfo, input, result, experience, cookingTime);
    }

    public RecipeType<SmeltingRecipe> getType() {
        return RecipeType.SMELTING;
    }
}
