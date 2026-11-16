package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.flag.FeatureFlagSet;

/** Kiln's stand-in for {@code StonecutterRecipeDisplay}: a stonecutter's input and result. */
public record StonecutterRecipeDisplay(SlotDisplay input, SlotDisplay result, SlotDisplay craftingStation)
        implements RecipeDisplay {
    public static final MapCodec<StonecutterRecipeDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SlotDisplay.CODEC.fieldOf("input").forGetter(StonecutterRecipeDisplay::input),
            SlotDisplay.CODEC.fieldOf("result").forGetter(StonecutterRecipeDisplay::result),
            SlotDisplay.CODEC.fieldOf("crafting_station").forGetter(StonecutterRecipeDisplay::craftingStation))
            .apply(i, StonecutterRecipeDisplay::new));
    public static final Type<StonecutterRecipeDisplay> TYPE = Type.kiln$of("stonecutter", MAP_CODEC);

    @Override
    public Type<StonecutterRecipeDisplay> type() {
        return TYPE;
    }

    @Override
    public boolean isEnabled(FeatureFlagSet flags) {
        return input.isEnabled(flags) && RecipeDisplay.super.isEnabled(flags);
    }
}
