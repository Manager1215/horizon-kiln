package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code BannerDuplicateRecipe} ({@code minecraft:crafting_special_bannerduplicate}): a patterned
 * banner and a blank one of its color make two of the patterned one. The game crafts it; Kiln's {@link #matches} only
 * counts the two banners.
 */
public class BannerDuplicateRecipe extends CustomRecipe {
    public static final MapCodec<BannerDuplicateRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.BannerDuplicateRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, BannerDuplicateRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.BannerDuplicateRecipe.STREAM_CODEC");
    public static final RecipeSerializer<BannerDuplicateRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient banner;
    public final ItemStackTemplate result;

    public BannerDuplicateRecipe(Ingredient banner, ItemStackTemplate result) {
        this.banner = banner;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return kiln$only(input, banner) && kiln$count(input, banner) == 2;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return kiln$first(input, banner).copyWithCount(1);
    }

    @Override
    public RecipeSerializer<BannerDuplicateRecipe> getSerializer() {
        return SERIALIZER;
    }
}
