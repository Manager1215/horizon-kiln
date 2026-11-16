package net.minecraft.world.item.crafting;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Kiln's stand-in for {@code TransmuteRecipe}: a crafting recipe that turns its input into another item with a
 * material (a bundle dyed), keeping the input's components.
 */
public class TransmuteRecipe extends NormalCraftingRecipe {
    public static final RecipeSerializer<TransmuteRecipe> SERIALIZER = new RecipeSerializer<>(
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.TransmuteRecipe.SERIALIZER"),
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.TransmuteRecipe.SERIALIZER"));

    public final Ingredient input;
    public final Ingredient material;
    /** As in the game: another item, or (without one, {@code map_cloning}'s {@code {}}) the input's own. */
    public final TransmuteResult result;

    /**
     * Kiln's own: the game's takes how many materials it takes and whether they add to the result's count too
     * ({@code MinMaxBounds.Ints} isn't written yet): one material, as most of the game's.
     */
    public TransmuteRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, Ingredient input,
            Ingredient material, TransmuteResult result) {
        super(commonInfo, bookInfo);
        this.input = input;
        this.material = material;
        this.result = result;
    }

    /** {@code result}'s item with {@code input}'s components, as the recipe makes it. */
    public static ItemStack createWithOriginalComponents(ItemStackTemplate result, ItemStack input) {
        return result.apply(input.getComponentsPatch());
    }

    @Override
    public RecipeSerializer<TransmuteRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
        int inputs = 0;
        int materials = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (this.input.test(stack) && inputs == 0) {
                inputs++;
            } else if (material.test(stack) && materials == 0) {
                materials++;
            } else {
                return false;
            }
        }
        return inputs == 1 && materials == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        for (ItemStack stack : input.items()) {
            if (this.input.test(stack)) {
                return createWithOriginalComponents(result.resolve(stack.typeHolder()), stack);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(List.of(input.display(), material.display()),
                result.item().isPresent()
                        ? new SlotDisplay.ItemStackSlotDisplay(result.resolve(result.item().get()))
                        : input.display(),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
}
