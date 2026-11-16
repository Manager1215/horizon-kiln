package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.flag.FeatureFlagSet;

/** Kiln's stand-in for {@code SmithingRecipeDisplay}: a smithing recipe's template, base, addition and result. */
public record SmithingRecipeDisplay(SlotDisplay template, SlotDisplay base, SlotDisplay addition, SlotDisplay result,
        SlotDisplay craftingStation) implements RecipeDisplay {
    public static final MapCodec<SmithingRecipeDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SlotDisplay.CODEC.fieldOf("template").forGetter(SmithingRecipeDisplay::template),
            SlotDisplay.CODEC.fieldOf("base").forGetter(SmithingRecipeDisplay::base),
            SlotDisplay.CODEC.fieldOf("addition").forGetter(SmithingRecipeDisplay::addition),
            SlotDisplay.CODEC.fieldOf("result").forGetter(SmithingRecipeDisplay::result),
            SlotDisplay.CODEC.fieldOf("crafting_station").forGetter(SmithingRecipeDisplay::craftingStation))
            .apply(i, SmithingRecipeDisplay::new));
    public static final Type<SmithingRecipeDisplay> TYPE = Type.kiln$of("smithing", MAP_CODEC);

    @Override
    public Type<SmithingRecipeDisplay> type() {
        return TYPE;
    }

    @Override
    public boolean isEnabled(FeatureFlagSet flags) {
        return template.isEnabled(flags) && base.isEnabled(flags) && addition.isEnabled(flags)
                && RecipeDisplay.super.isEnabled(flags);
    }
}
