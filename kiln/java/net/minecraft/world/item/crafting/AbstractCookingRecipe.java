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

    /** What a recipe viewer shows: the ingredient, any fuel, the result, at its block. */
    @Override
    public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        return java.util.List.of(new net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay(input().display(),
                net.minecraft.world.item.crafting.display.SlotDisplay.AnyFuel.INSTANCE,
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(result()),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(kiln$station()),
                cookingTime, experience));
    }

    /** Kiln's own: the block it cooks in, as an item. */
    protected net.minecraft.world.item.Item kiln$station() {
        return net.minecraft.world.item.Items.FURNACE;
    }

    public record CookingBookInfo(CookingBookCategory category, String group) implements BookInfo<CookingBookCategory> {}
}
