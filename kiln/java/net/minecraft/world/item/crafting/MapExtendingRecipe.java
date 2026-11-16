package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code MapExtendingRecipe} ({@code minecraft:crafting_special_mapextending}): a filled map
 * ringed by eight papers makes the map zoomed out. The game crafts it (and scales the map); Kiln's {@link #assemble}
 * gives the map as it was.
 */
public class MapExtendingRecipe extends CustomRecipe {
    public static final MapCodec<MapExtendingRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.MapExtendingRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, MapExtendingRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.MapExtendingRecipe.STREAM_CODEC");
    public static final RecipeSerializer<MapExtendingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient map;
    public final Ingredient material;
    public final TransmuteResult result;

    public MapExtendingRecipe(Ingredient map, Ingredient material, TransmuteResult result) {
        this.map = map;
        this.material = material;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3 || !map.test(input.getItem(4))) {
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
        ItemStack found = input.size() > 4 ? input.getItem(4) : ItemStack.EMPTY;
        return found.isEmpty() ? ItemStack.EMPTY
                : result.resolve(found.typeHolder(), 1).apply(found.getComponentsPatch());
    }

    @Override
    public RecipeSerializer<MapExtendingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
