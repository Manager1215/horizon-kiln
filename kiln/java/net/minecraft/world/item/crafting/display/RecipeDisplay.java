package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Kiln's stand-in for {@code RecipeDisplay}: how a recipe looks in a recipe book or viewer (its slots, what it makes,
 * at which block), as vanilla's.
 */
public interface RecipeDisplay {
    SlotDisplay result();

    SlotDisplay craftingStation();

    Type<? extends RecipeDisplay> type();

    default boolean isEnabled(FeatureFlagSet flags) {
        return result().isEnabled(flags) && craftingStation().isEnabled(flags);
    }

    record Type<T extends RecipeDisplay>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
        /** Kiln's own: a type whose stream codec is a name only (Kiln sends no displays over the network). */
        static <T extends RecipeDisplay> Type<T> kiln$of(String id, MapCodec<T> codec) {
            return new Type<>(codec, kiln.Constants.streamCodec("RecipeDisplay " + id));
        }
    }
}
