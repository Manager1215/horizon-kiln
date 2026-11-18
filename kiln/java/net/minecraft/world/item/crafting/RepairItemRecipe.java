package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code RepairItemRecipe} ({@code minecraft:crafting_special_repairitem}): two worn items of one
 * kind make one with both durabilities and a twentieth more, keeping only the curses either had (each at the higher
 * level), as the game makes it (D-197).
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
        int max = Math.max(pair[0].getMaxDamage(), pair[1].getMaxDamage());
        int left = (pair[0].getMaxDamage() - pair[0].getDamageValue())
                + (pair[1].getMaxDamage() - pair[1].getDamageValue()) + max * 5 / 100;
        ItemStack out = new ItemStack(pair[0].getItem());
        out.setDamageValue(Math.max(0, max - left));
        net.minecraft.world.item.enchantment.ItemEnchantments.Mutable curses =
                new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (ItemStack one : pair) {
            net.minecraft.world.item.enchantment.ItemEnchantments enchantments = one.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            if (enchantments == null) {
                continue;
            }
            for (var entry : enchantments.entrySet()) {
                if (entry.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) {
                    curses.upgrade(entry.getKey(), entry.getIntValue());
                }
            }
        }
        if (!curses.keySet().isEmpty()) {
            out.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, curses.toImmutable());
        }
        return out;
    }

    @Override
    public RecipeSerializer<RepairItemRecipe> getSerializer() {
        return SERIALIZER;
    }
}
