package net.minecraft.world.item.crafting;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

/**
 * Kiln's stand-in for {@code CustomRecipe}: a special crafting recipe, made in code (a banner copied, a firework). As
 * in the game, it has no display and the recipe book doesn't show it: a recipe viewer shows the ones it knows by
 * their class (D-157).
 */
public abstract class CustomRecipe implements CraftingRecipe {
    public CustomRecipe() {}

    @Override
    public boolean isSpecial() {
        return true;
    }

    public boolean showNotification() {
        return true;
    }

    public String group() {
        return "";
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public abstract RecipeSerializer<? extends CustomRecipe> getSerializer();

    @Override
    public List<RecipeDisplay> display() {
        return List.of();
    }

    /** Kiln's own: how many of the grid's stacks {@code ingredient} takes. */
    protected static int kiln$count(CraftingInput input, Ingredient ingredient) {
        int n = 0;
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                n++;
            }
        }
        return n;
    }

    /** Kiln's own: whether each of the grid's stacks is one that one of {@code allowed} takes. */
    protected static boolean kiln$only(CraftingInput input, Ingredient... allowed) {
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            boolean taken = false;
            for (Ingredient ingredient : allowed) {
                taken |= ingredient.test(stack);
            }
            if (!taken) {
                return false;
            }
        }
        return true;
    }

    /** Kiln's own: the colors of the grid's stacks {@code dye} takes, in the grid's order (their {@code dye}). */
    protected static java.util.List<net.minecraft.world.item.DyeColor> kiln$dyes(CraftingInput input,
            Ingredient dye) {
        java.util.List<net.minecraft.world.item.DyeColor> colors = new java.util.ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty() && dye.test(stack)) {
                net.minecraft.world.item.DyeColor color = stack.get(net.minecraft.core.component.DataComponents.DYE);
                if (color != null) {
                    colors.add(color);
                }
            }
        }
        return colors;
    }

    /** Kiln's own: how many banner pattern layers a stack has (its {@code banner_patterns}, as saved). */
    protected static int kiln$layers(ItemStack stack) {
        Object layers = stack.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
        return layers instanceof com.google.gson.JsonArray array ? array.size() : 0;
    }

    /** Kiln's own: the grid's first stack that {@code ingredient} takes, or the empty stack. */
    protected static ItemStack kiln$first(CraftingInput input, Ingredient ingredient) {
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
