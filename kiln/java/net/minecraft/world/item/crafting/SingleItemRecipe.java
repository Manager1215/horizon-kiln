package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code SingleItemRecipe}: one ingredient, one result (cooking, the stonecutter). */
public abstract class SingleItemRecipe implements Recipe<SingleRecipeInput> {
    private final CommonInfo commonInfo;
    private final Ingredient input;
    private final ItemStackTemplate result;

    public SingleItemRecipe(CommonInfo commonInfo, Ingredient input, ItemStackTemplate result) {
        this.commonInfo = commonInfo;
        this.input = input;
        this.result = result;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    public boolean showNotification() {
        return commonInfo.showNotification();
    }

    public Ingredient input() {
        return input;
    }

    /** Kiln's own: what it makes. */
    public ItemStackTemplate kiln$result() {
        return result;
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result.create();
    }
}
