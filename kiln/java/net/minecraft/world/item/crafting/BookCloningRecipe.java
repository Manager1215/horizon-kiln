package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code BookCloningRecipe} ({@code minecraft:crafting_special_bookcloning}): a written book and
 * book and quills make copies of it. The game crafts it and checks the book's generation, which Kiln doesn't (its
 * {@code MinMaxBounds.Ints} isn't written yet).
 */
public class BookCloningRecipe extends CustomRecipe {
    public static final MapCodec<BookCloningRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.BookCloningRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, BookCloningRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.BookCloningRecipe.STREAM_CODEC");
    public static final RecipeSerializer<BookCloningRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient source;
    public final Ingredient material;
    public final ItemStackTemplate result;

    /** Kiln's own: the game's takes the generations allowed too. */
    public BookCloningRecipe(Ingredient source, Ingredient material, ItemStackTemplate result) {
        this.source = source;
        this.material = material;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return kiln$only(input, source, material) && kiln$count(input, source) == 1 && kiln$count(input, material) >= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack book = kiln$first(input, source);
        return book.isEmpty() ? ItemStack.EMPTY
                : result.apply(book.getComponentsPatch()).copyWithCount(kiln$count(input, material));
    }

    @Override
    public RecipeSerializer<BookCloningRecipe> getSerializer() {
        return SERIALIZER;
    }
}
