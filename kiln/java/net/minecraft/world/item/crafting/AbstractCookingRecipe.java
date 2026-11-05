package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStackTemplate;

/** Kiln's stand-in for {@code AbstractCookingRecipe}: a furnace's, a blast furnace's, a smoker's or a campfire's. */
public abstract class AbstractCookingRecipe extends SingleItemRecipe {
    private final CookingBookInfo bookInfo;
    private final float experience;
    private final int cookingTime;

    public AbstractCookingRecipe(CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient input,
            ItemStackTemplate result, float experience, int cookingTime) {
        super(commonInfo, input, result);
        this.bookInfo = bookInfo;
        this.experience = experience;
        this.cookingTime = cookingTime;
    }

    public float experience() {
        return experience;
    }

    public int cookingTime() {
        return cookingTime;
    }

    public CookingBookCategory category() {
        return bookInfo.category();
    }

    public String group() {
        return bookInfo.group();
    }

    public record CookingBookInfo(CookingBookCategory category, String group) implements BookInfo<CookingBookCategory> {}
}
