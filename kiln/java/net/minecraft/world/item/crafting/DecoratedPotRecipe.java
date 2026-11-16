package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code DecoratedPotRecipe} ({@code minecraft:crafting_decorated_pot}): four sherds or bricks in a
 * diamond make a pot with them on its sides. The game crafts it; Kiln's {@link #assemble} gives the plain pot (Kiln
 * has no pot decorations component yet).
 */
public class DecoratedPotRecipe extends CustomRecipe {
    public static final MapCodec<DecoratedPotRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.DecoratedPotRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, DecoratedPotRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.DecoratedPotRecipe.STREAM_CODEC");
    public static final RecipeSerializer<DecoratedPotRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public final Ingredient backPattern;
    public final Ingredient leftPattern;
    public final Ingredient rightPattern;
    public final Ingredient frontPattern;
    public final ItemStackTemplate result;

    public DecoratedPotRecipe(Ingredient pattern, ItemStackTemplate result) {
        this(pattern, pattern, pattern, pattern, result);
    }

    public DecoratedPotRecipe(Ingredient back, Ingredient left, Ingredient right, Ingredient front,
            ItemStackTemplate result) {
        this.backPattern = back;
        this.leftPattern = left;
        this.rightPattern = right;
        this.frontPattern = front;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = input.getItem(i);
            boolean ok = switch (i) {
                case 1 -> backPattern.test(stack);
                case 3 -> leftPattern.test(stack);
                case 5 -> rightPattern.test(stack);
                case 7 -> frontPattern.test(stack);
                default -> stack.isEmpty();
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.create();
    }

    @Override
    public RecipeSerializer<DecoratedPotRecipe> getSerializer() {
        return SERIALIZER;
    }
}
