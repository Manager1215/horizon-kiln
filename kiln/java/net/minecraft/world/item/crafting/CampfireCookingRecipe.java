package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStackTemplate;

/** Kiln's stand-in for {@code CampfireCookingRecipe}: a campfire's recipe. */
public class CampfireCookingRecipe extends AbstractCookingRecipe {
    public CampfireCookingRecipe(CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient input, ItemStackTemplate result,
            float experience, int cookingTime) {
        super(commonInfo, bookInfo, input, result, experience, cookingTime);
    }

    public RecipeType<CampfireCookingRecipe> getType() {
        return RecipeType.CAMPFIRE_COOKING;
    }
}
