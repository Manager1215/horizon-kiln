package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.flag.FeatureFlagSet;

/** Kiln's stand-in for {@code ShapedCraftingRecipeDisplay}: a shaped recipe's grid, row by row. */
public record ShapedCraftingRecipeDisplay(int width, int height, List<SlotDisplay> ingredients, SlotDisplay result,
        SlotDisplay craftingStation) implements RecipeDisplay {
    public static final MapCodec<ShapedCraftingRecipeDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("width").forGetter(ShapedCraftingRecipeDisplay::width),
            Codec.INT.fieldOf("height").forGetter(ShapedCraftingRecipeDisplay::height),
            SlotDisplay.CODEC.listOf().fieldOf("ingredients").forGetter(ShapedCraftingRecipeDisplay::ingredients),
            SlotDisplay.CODEC.fieldOf("result").forGetter(ShapedCraftingRecipeDisplay::result),
            SlotDisplay.CODEC.fieldOf("crafting_station").forGetter(ShapedCraftingRecipeDisplay::craftingStation))
            .apply(i, ShapedCraftingRecipeDisplay::new));
    public static final Type<ShapedCraftingRecipeDisplay> TYPE = Type.kiln$of("crafting_shaped", MAP_CODEC);

    public ShapedCraftingRecipeDisplay {
        if (ingredients.size() != width * height) {
            throw new IllegalArgumentException("Invalid shaped recipe display contents");
        }
        ingredients = List.copyOf(ingredients);
    }

    @Override
    public Type<ShapedCraftingRecipeDisplay> type() {
        return TYPE;
    }

    @Override
    public boolean isEnabled(FeatureFlagSet flags) {
        return ingredients.stream().allMatch(d -> d.isEnabled(flags)) && RecipeDisplay.super.isEnabled(flags);
    }
}
