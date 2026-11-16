package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code FireworkRocketRecipe} ({@code minecraft:crafting_special_firework_rocket}): paper, one to
 * three gunpowder and firework stars make rockets. The game crafts it; Kiln's {@link #assemble} gives the plain
 * rockets. A recipe viewer reads its fields (the game's are private, opened by its access widener).
 */
public class FireworkRocketRecipe extends CustomRecipe {
    public static final MapCodec<FireworkRocketRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.FireworkRocketRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, FireworkRocketRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.FireworkRocketRecipe.STREAM_CODEC");
    public static final RecipeSerializer<FireworkRocketRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient shell;
    public final Ingredient fuel;
    public final Ingredient star;
    public final ItemStackTemplate result;

    public FireworkRocketRecipe(Ingredient shell, Ingredient fuel, Ingredient star, ItemStackTemplate result) {
        this.shell = shell;
        this.fuel = fuel;
        this.star = star;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int fuels = kiln$count(input, fuel);
        return kiln$only(input, shell, fuel, star) && kiln$count(input, shell) == 1 && fuels >= 1 && fuels <= 3;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.create();
    }

    @Override
    public RecipeSerializer<FireworkRocketRecipe> getSerializer() {
        return SERIALIZER;
    }
}
