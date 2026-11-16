package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code ShieldDecorationRecipe} ({@code minecraft:crafting_special_shielddecoration}): a shield and
 * a banner make the shield with the banner's patterns. The game crafts it; Kiln's {@link #assemble} gives the shield
 * as it was. A recipe viewer reads its fields and makes one recipe a banner.
 */
public class ShieldDecorationRecipe extends CustomRecipe {
    public static final MapCodec<ShieldDecorationRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.ShieldDecorationRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldDecorationRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.ShieldDecorationRecipe.STREAM_CODEC");
    public static final RecipeSerializer<ShieldDecorationRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient banner;
    public final Ingredient target;
    public final ItemStackTemplate result;

    public ShieldDecorationRecipe(Ingredient banner, Ingredient target, ItemStackTemplate result) {
        this.banner = banner;
        this.target = target;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return kiln$only(input, banner, target) && kiln$count(input, banner) == 1 && kiln$count(input, target) == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack shield = kiln$first(input, target);
        return shield.isEmpty() ? ItemStack.EMPTY : result.apply(shield.getComponentsPatch());
    }

    @Override
    public RecipeSerializer<ShieldDecorationRecipe> getSerializer() {
        return SERIALIZER;
    }
}
