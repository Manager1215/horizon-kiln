package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code DyeRecipe} ({@code minecraft:crafting_dye}): a dyeable item (leather armor) and dyes make
 * it dyed. Unlike the other special ones the recipe book shows it. The game crafts it and mixes the colors; Kiln's
 * {@link #assemble} gives the item as it was, and its display shows the dyes and the item, not the game's demo of a
 * dyed one (D-157).
 */
public class DyeRecipe extends NormalCraftingRecipe {
    public static final MapCodec<DyeRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.DyeRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, DyeRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.DyeRecipe.STREAM_CODEC");
    public static final RecipeSerializer<DyeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient target;
    public final Ingredient dye;
    public final ItemStackTemplate result;

    public DyeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, Ingredient target,
            Ingredient dye, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.target = target;
        this.dye = dye;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return CustomRecipe.kiln$only(input, target, dye) && CustomRecipe.kiln$count(input, target) == 1
                && CustomRecipe.kiln$count(input, dye) >= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack found = CustomRecipe.kiln$first(input, target);
        return found.isEmpty() ? ItemStack.EMPTY : result.apply(found.getComponentsPatch());
    }

    @Override
    public RecipeSerializer<DyeRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(List.of(dye.display(), target.display()),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
}
