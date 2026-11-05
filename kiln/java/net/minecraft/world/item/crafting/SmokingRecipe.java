package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStackTemplate;

/** Kiln's stand-in for {@code SmokingRecipe}: a smoker's recipe. */
public class SmokingRecipe extends AbstractCookingRecipe {
    public SmokingRecipe(CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient input, ItemStackTemplate result,
            float experience, int cookingTime) {
        super(commonInfo, bookInfo, input, result, experience, cookingTime);
    }

    public RecipeType<SmokingRecipe> getType() {
        return RecipeType.SMOKING;
    }
}
