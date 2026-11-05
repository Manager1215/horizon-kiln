package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStackTemplate;

/** Kiln's stand-in for {@code BlastingRecipe}: a blast furnace's recipe. */
public class BlastingRecipe extends AbstractCookingRecipe {
    public BlastingRecipe(CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient input, ItemStackTemplate result,
            float experience, int cookingTime) {
        super(commonInfo, bookInfo, input, result, experience, cookingTime);
    }

    public RecipeType<BlastingRecipe> getType() {
        return RecipeType.BLASTING;
    }
}
