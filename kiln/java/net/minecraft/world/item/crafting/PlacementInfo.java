package net.minecraft.world.item.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Kiln's stand-in for {@code PlacementInfo}: a recipe's ingredients as they go into a grid, and for each of the
 * grid's slots the index of its ingredient (-1 for an empty one). A recipe viewer's server side makes a crafting
 * recipe's display from it (REI's, D-234).
 */
public class PlacementInfo {
    public static final int EMPTY_SLOT = -1;
    public static final PlacementInfo NOT_PLACEABLE = new PlacementInfo(List.of(), IntList.of());

    private final List<Ingredient> ingredients;
    private final IntList slotsToIngredientIndex;

    private PlacementInfo(List<Ingredient> ingredients, IntList slotsToIngredientIndex) {
        this.ingredients = ingredients;
        this.slotsToIngredientIndex = slotsToIngredientIndex;
    }

    /** Every ingredient in a slot of its own, in order. */
    public static PlacementInfo create(List<Ingredient> ingredients) {
        if (ingredients.isEmpty()) {
            return NOT_PLACEABLE;
        }
        IntList slots = new IntArrayList(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            slots.add(i);
        }
        return new PlacementInfo(List.copyOf(ingredients), slots);
    }

    /** A pattern's slots, an empty one with no ingredient. */
    public static PlacementInfo createFromOptionals(List<Optional<Ingredient>> slots) {
        List<Ingredient> ingredients = new ArrayList<>();
        IntList indices = new IntArrayList(slots.size());
        for (Optional<Ingredient> slot : slots) {
            if (slot.isPresent()) {
                indices.add(ingredients.size());
                ingredients.add(slot.get());
            } else {
                indices.add(EMPTY_SLOT);
            }
        }
        return ingredients.isEmpty() ? NOT_PLACEABLE : new PlacementInfo(List.copyOf(ingredients), indices);
    }

    public IntList slotsToIngredientIndex() {
        return slotsToIngredientIndex;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    public boolean isImpossibleToPlace() {
        return slotsToIngredientIndex.isEmpty();
    }
}
