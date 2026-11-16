package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code RepairItemRecipe} ({@code minecraft:crafting_special_repairitem}): two damaged items of
 * a kind make one with their durability summed (and five percent more). The game crafts it; Kiln's {@link #assemble}
 * adds the durability, without the game's enchantments rule.
 */
public class RepairItemRecipe extends CustomRecipe {
    public static final RepairItemRecipe INSTANCE = new RepairItemRecipe();
    public static final MapCodec<RepairItemRecipe> MAP_CODEC =
            kiln.Constants.mapCodec("net.minecraft.world.item.crafting.RepairItemRecipe.MAP_CODEC");
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairItemRecipe> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.RepairItemRecipe.STREAM_CODEC");
    public static final RecipeSerializer<RepairItemRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public RepairItemRecipe() {}

    /** The grid's two stacks, if it holds exactly two damageable stacks of one item, else null. */
    private static ItemStack[] pair(CraftingInput input) {
        ItemStack[] found = new ItemStack[2];
        int n = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (n == 2 || stack.getCount() != 1 || !stack.isDamageableItem()) {
                return null;
            }
            found[n++] = stack;
        }
        return n == 2 && ItemStack.isSameItem(found[0], found[1]) ? found : null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pair(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack[] pair = pair(input);
        if (pair == null) {
            return ItemStack.EMPTY;
        }
        int max = pair[0].getMaxDamage();
        int left = (max - pair[0].getDamageValue()) + (max - pair[1].getDamageValue()) + max * 5 / 100;
        ItemStack out = new ItemStack(pair[0].getItem());
        out.setDamageValue(Math.max(0, max - left));
        return out;
    }

    @Override
    public RecipeSerializer<RepairItemRecipe> getSerializer() {
        return SERIALIZER;
    }
}
