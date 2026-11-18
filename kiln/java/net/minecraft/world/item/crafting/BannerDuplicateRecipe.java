package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code BannerDuplicateRecipe} ({@code minecraft:crafting_special_bannerduplicate}): a banner
 * with patterns (six at most) and a blank one of its color make a copy of the patterned one, which stays in the grid,
 * as in the game (D-197).
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
        boolean patterned = false;
        boolean blank = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!banner.test(stack)) {
                return false;
            }
            int layers = kiln$layers(stack);
            if (layers > 6) {
                return false;
            }
            if (layers > 0) {
                if (patterned) {
                    return false;
                }
                patterned = true;
            } else {
                if (blank) {
                    return false;
                }
                blank = true;
            }
        }
        return patterned && blank;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty() && banner.test(stack) && kiln$layers(stack) > 0) {
                return result.apply(stack.getComponentsPatch());
            }
        }
        return ItemStack.EMPTY;
    }

    /** The patterned banner stays in the grid. */
    @Override
    public net.minecraft.core.NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        net.minecraft.core.NonNullList<ItemStack> left = super.getRemainingItems(input);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && kiln$layers(stack) > 0) {
                left.set(i, stack.copyWithCount(1));
            }
        }
        return left;
    }

    @Override
    public RecipeSerializer<BannerDuplicateRecipe> getSerializer() {
        return SERIALIZER;
    }
}
