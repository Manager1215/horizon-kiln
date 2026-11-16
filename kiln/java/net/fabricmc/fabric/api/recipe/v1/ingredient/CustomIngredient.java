package net.fabricmc.fabric.api.recipe.v1.ingredient;

import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for Fabric's {@code CustomIngredient}: an ingredient of a mod's own logic. Kiln's ingredients are
 * the game's kinds (items, a tag), so {@code Ingredient.getCustomIngredient()} is always null; a mod asks it before
 * it lists an ingredient's items (Reborn Core's recipes in a recipe viewer, D-165).
 */
public interface CustomIngredient {
    boolean test(ItemStack stack);

    Stream<Holder<Item>> items();

    boolean requiresTesting();

    CustomIngredientSerializer<?> getSerializer();

    /** How a recipe viewer shows it: Kiln has no slot display of its own for one. */
    default net.minecraft.world.item.crafting.display.SlotDisplay display() {
        kiln.Missing.called("net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient.display");
        return null;
    }
}
