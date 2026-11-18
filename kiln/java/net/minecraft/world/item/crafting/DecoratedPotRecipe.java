package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code DecoratedPotRecipe} ({@code minecraft:crafting_decorated_pot}): four sherds or bricks in a
 * diamond make a pot with them on its sides ({@code pot_decorations}, as the game saves it, D-197).
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
        ItemStack pot = result.create();
        if (input.width() != 3 || input.height() != 3) {
            return pot;
        }
        // As the game saves them (D-197): back, left, right, front, a brick for a plain side; all bricks is a plain pot.
        com.google.gson.JsonArray sides = new com.google.gson.JsonArray();
        boolean decorated = false;
        for (int i : new int[] {1, 3, 5, 7}) {
            ItemStack side = input.getItem(i);
            net.minecraft.resources.Identifier id = side.isEmpty() ? null : side.getItem().kiln$id();
            String name = id == null ? "minecraft:brick" : id.toString();
            decorated |= !name.equals("minecraft:brick");
            sides.add(name);
        }
        if (decorated) {
            pot.set(net.minecraft.core.component.DataComponents.POT_DECORATIONS, sides);
        }
        return pot;
    }

    @Override
    public RecipeSerializer<DecoratedPotRecipe> getSerializer() {
        return SERIALIZER;
    }
}
