package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code FireworkStarRecipe} ({@code minecraft:crafting_special_firework_star}): gunpowder, dyes
 * and at most one shape item, trail item and twinkle item make a firework star. The game crafts it; Kiln's {@link
 * #assemble} gives a plain star. A recipe viewer reads its fields.
 */
public class FireworkStarRecipe extends CustomRecipe {
    public static final MapCodec<FireworkStarRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.FireworkStarRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, FireworkStarRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.FireworkStarRecipe.STREAM_CODEC");
    public static final RecipeSerializer<FireworkStarRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Map<FireworkExplosion.Shape, Ingredient> shapes;
    public final Ingredient trail;
    public final Ingredient twinkle;
    public final Ingredient fuel;
    public final Ingredient dye;
    public final ItemStackTemplate result;

    public FireworkStarRecipe(Map<FireworkExplosion.Shape, Ingredient> shapes, Ingredient trail, Ingredient twinkle,
            Ingredient fuel, Ingredient dye, ItemStackTemplate result) {
        this.shapes = Map.copyOf(shapes);
        this.trail = trail;
        this.twinkle = twinkle;
        this.fuel = fuel;
        this.dye = dye;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int shaped = 0;
        for (Ingredient shape : shapes.values()) {
            shaped += kiln$count(input, shape);
        }
        Ingredient[] allowed = new Ingredient[shapes.size() + 4];
        int i = 0;
        for (Ingredient shape : shapes.values()) {
            allowed[i++] = shape;
        }
        allowed[i++] = trail;
        allowed[i++] = twinkle;
        allowed[i++] = fuel;
        allowed[i] = dye;
        return kiln$only(input, allowed) && kiln$count(input, fuel) == 1 && kiln$count(input, dye) >= 1 && shaped <= 1
                && kiln$count(input, trail) <= 1 && kiln$count(input, twinkle) <= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.create();
    }

    @Override
    public RecipeSerializer<FireworkStarRecipe> getSerializer() {
        return SERIALIZER;
    }
}
