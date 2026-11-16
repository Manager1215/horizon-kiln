package net.minecraft.world.item.crafting;

import java.util.Optional;
import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code SmithingRecipe}: a smithing table's recipe, a template, a base and an addition. */
public interface SmithingRecipe extends Recipe<SmithingRecipeInput> {
    @Override
    default RecipeType<SmithingRecipe> getType() {
        return RecipeType.SMITHING;
    }

    @Override
    default boolean matches(SmithingRecipeInput input, Level level) {
        return Ingredient.testOptionalIngredient(templateIngredient(), input.template())
                && baseIngredient().test(input.base())
                && Ingredient.testOptionalIngredient(additionIngredient(), input.addition());
    }

    Optional<Ingredient> templateIngredient();

    Ingredient baseIngredient();

    Optional<Ingredient> additionIngredient();

    default boolean isSpecial() {
        return true;
    }
}
