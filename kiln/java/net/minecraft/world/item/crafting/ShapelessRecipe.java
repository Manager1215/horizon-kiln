package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/** Kiln's stand-in for {@code ShapelessRecipe}: a crafting recipe whose ingredients go anywhere in the grid. */
public class ShapelessRecipe extends NormalCraftingRecipe {
    public static final RecipeSerializer<ShapelessRecipe> SERIALIZER = new RecipeSerializer<>(
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.ShapelessRecipe.SERIALIZER"),
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.ShapelessRecipe.SERIALIZER"));

    private final ItemStackTemplate result;
    private final List<Ingredient> ingredients;

    public ShapelessRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ItemStackTemplate result, List<Ingredient> ingredients) {
        super(commonInfo, bookInfo);
        this.result = result;
        this.ingredients = List.copyOf(ingredients);
    }

    @Override
    public RecipeSerializer<ShapelessRecipe> getSerializer() {
        return SERIALIZER;
    }

    /** Its ingredients, each in a slot of its own, as a recipe viewer places them (REI's server side, D-234). */
    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredients);
    }

    /** Kiln's own: its ingredients. */
    public List<Ingredient> kiln$ingredients() {
        return ingredients;
    }

    /** Whether the grid's stacks are its ingredients, one each, in any place. */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks.size() == ingredients.size() && assign(stacks, 0, new boolean[ingredients.size()]);
    }

    private boolean assign(List<ItemStack> stacks, int index, boolean[] used) {
        if (index == stacks.size()) {
            return true;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            if (!used[i] && ingredients.get(i).test(stacks.get(index))) {
                used[i] = true;
                if (assign(stacks, index + 1, used)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.create();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(ingredients.stream().map(Ingredient::display).toList(),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
}
