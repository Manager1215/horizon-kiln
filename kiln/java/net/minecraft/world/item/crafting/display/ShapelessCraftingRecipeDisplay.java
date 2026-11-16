package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.flag.FeatureFlagSet;

/** Kiln's stand-in for {@code ShapelessCraftingRecipeDisplay}: a shapeless recipe's ingredients. */
public record ShapelessCraftingRecipeDisplay(List<SlotDisplay> ingredients, SlotDisplay result,
        SlotDisplay craftingStation) implements RecipeDisplay {
    public static final MapCodec<ShapelessCraftingRecipeDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SlotDisplay.CODEC.listOf().fieldOf("ingredients").forGetter(ShapelessCraftingRecipeDisplay::ingredients),
            SlotDisplay.CODEC.fieldOf("result").forGetter(ShapelessCraftingRecipeDisplay::result),
            SlotDisplay.CODEC.fieldOf("crafting_station").forGetter(ShapelessCraftingRecipeDisplay::craftingStation))
            .apply(i, ShapelessCraftingRecipeDisplay::new));
    public static final Type<ShapelessCraftingRecipeDisplay> TYPE = Type.kiln$of("crafting_shapeless", MAP_CODEC);

    public ShapelessCraftingRecipeDisplay {
        ingredients = List.copyOf(ingredients);
    }

    @Override
    public Type<ShapelessCraftingRecipeDisplay> type() {
        return TYPE;
    }

    @Override
    public boolean isEnabled(FeatureFlagSet flags) {
        return ingredients.stream().allMatch(d -> d.isEnabled(flags)) && RecipeDisplay.super.isEnabled(flags);
    }
}
