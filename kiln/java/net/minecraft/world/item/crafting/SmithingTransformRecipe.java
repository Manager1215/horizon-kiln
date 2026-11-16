package net.minecraft.world.item.crafting;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

/**
 * Kiln's stand-in for {@code SmithingTransformRecipe}: a smithing recipe that turns the base into its result (a
 * diamond tool into a netherite one), keeping the base's components.
 */
public class SmithingTransformRecipe implements SmithingRecipe {
    public final Recipe.CommonInfo commonInfo;
    public final Optional<Ingredient> template;
    public final Ingredient base;
    public final Optional<Ingredient> addition;
    public final ItemStackTemplate result;

    public SmithingTransformRecipe(Recipe.CommonInfo commonInfo, Optional<Ingredient> template, Ingredient base,
            Optional<Ingredient> addition, ItemStackTemplate result) {
        this.commonInfo = commonInfo;
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        return result.apply(input.base().getComponentsPatch());
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return template;
    }

    @Override
    public Ingredient baseIngredient() {
        return base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return addition;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new SmithingRecipeDisplay(Ingredient.optionalIngredientToDisplay(template), base.display(),
                Ingredient.optionalIngredientToDisplay(addition), new SlotDisplay.ItemStackSlotDisplay(result),
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }
}
