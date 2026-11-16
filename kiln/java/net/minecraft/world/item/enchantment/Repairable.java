package net.minecraft.world.item.enchantment;

import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code Repairable}, an item's {@code minecraft:repairable} component: the items that repair it
 * in an anvil. Kiln reads a game item's from {@code items.json} ({@code kiln.ClientComponents}).
 */
public record Repairable(HolderSet<Item> items) {
    public boolean isValidRepairItem(ItemStack stack) {
        return items.contains(stack.typeHolder());
    }
}
