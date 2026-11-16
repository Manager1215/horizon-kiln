package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.flag.FeatureFlagSet;

/** Kiln's stand-in for {@code FurnaceRecipeDisplay}: a cooking recipe's slots, time and experience. */
public record FurnaceRecipeDisplay(SlotDisplay ingredient, SlotDisplay fuel, SlotDisplay result,
        SlotDisplay craftingStation, int duration, float experience) implements RecipeDisplay {
    public static final MapCodec<FurnaceRecipeDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SlotDisplay.CODEC.fieldOf("ingredient").forGetter(FurnaceRecipeDisplay::ingredient),
            SlotDisplay.CODEC.fieldOf("fuel").forGetter(FurnaceRecipeDisplay::fuel),
            SlotDisplay.CODEC.fieldOf("result").forGetter(FurnaceRecipeDisplay::result),
            SlotDisplay.CODEC.fieldOf("crafting_station").forGetter(FurnaceRecipeDisplay::craftingStation),
            Codec.INT.fieldOf("duration").forGetter(FurnaceRecipeDisplay::duration),
            Codec.FLOAT.fieldOf("experience").forGetter(FurnaceRecipeDisplay::experience))
            .apply(i, FurnaceRecipeDisplay::new));
    public static final Type<FurnaceRecipeDisplay> TYPE = Type.kiln$of("furnace", MAP_CODEC);

    @Override
    public Type<FurnaceRecipeDisplay> type() {
        return TYPE;
    }

    @Override
    public boolean isEnabled(FeatureFlagSet flags) {
        return ingredient.isEnabled(flags) && fuel.isEnabled(flags) && RecipeDisplay.super.isEnabled(flags);
    }
}
