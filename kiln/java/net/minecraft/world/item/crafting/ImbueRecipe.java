package net.minecraft.world.item.crafting;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

/**
 * Kiln's stand-in for {@code ImbueRecipe}: a crafting recipe that imbues eight of a material with a source (arrows
 * with a lingering potion), the result taking the source's contents. The game crafts it; a recipe viewer shows one
 * recipe a potion of its own.
 */
public class ImbueRecipe extends NormalCraftingRecipe {
    public static final RecipeSerializer<ImbueRecipe> SERIALIZER = new RecipeSerializer<>(
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.ImbueRecipe.SERIALIZER"),
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.ImbueRecipe.SERIALIZER"));

    public final Ingredient source;
    public final Ingredient material;
    public final ItemStackTemplate result;

    public ImbueRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, Ingredient source,
            Ingredient material, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.source = source;
        this.material = material;
        this.result = result;
    }

    @Override
    public RecipeSerializer<ImbueRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
        if (input.width() != 3 || input.height() != 3 || !source.test(input.getItem(4))) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            if (i != 4 && !material.test(input.getItem(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.apply(input.getItem(4).getComponentsPatch());
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of();
    }
}
