package net.minecraft.world.inventory;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Kiln's stand-in for {@code GrindstoneMenu}: the grindstone's result for two inputs, as the game works it out (the
 * enchantments come off but the curses, two of an item join their durability). The game runs the real grindstone;
 * this one is for a mod that asks (a recipe viewer).
 */
public class GrindstoneMenu extends AbstractContainerMenu {
    private static final TagKey<Enchantment> CURSE =
            TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

    public GrindstoneMenu(int containerId, Inventory inventory) {
        this(MenuType.GRINDSTONE, containerId);
    }

    /** Kiln's own: a grindstone menu of {@code menuType}. */
    protected GrindstoneMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** What the grindstone makes of {@code top} and {@code bottom}; empty when it makes nothing. */
    public ItemStack computeResult(ItemStack top, ItemStack bottom) {
        boolean one = top.isEmpty() || bottom.isEmpty();
        if (top.isEmpty() && bottom.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (one) {
            ItemStack input = top.isEmpty() ? bottom : top;
            return EnchantmentHelper.getEnchantmentsForCrafting(input).isEmpty() ? ItemStack.EMPTY
                    : removeNonCurses(input.copy());
        }
        if (top.getCount() > 1 || bottom.getCount() > 1 || !top.is(bottom.getItem())) {
            return ItemStack.EMPTY;
        }
        ItemStack result = top.copyWithCount(1);
        int max = Math.max(top.getMaxDamage(), bottom.getMaxDamage());
        if (top.isDamageableItem()) {
            int left = max - top.getDamageValue() + max - bottom.getDamageValue() + max * 5 / 100;
            result.setDamageValue(Math.max(max - left, 0));
        }
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(top));
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(bottom).entrySet()) {
            if (entry.getKey().is(CURSE)) {
                merged.upgrade(entry.getKey(), entry.getIntValue());
            }
        }
        EnchantmentHelper.setEnchantments(result, merged.toImmutable());
        return removeNonCurses(result);
    }

    private static ItemStack removeNonCurses(ItemStack stack) {
        ItemEnchantments.Mutable kept = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
        kept.removeIf(enchantment -> !enchantment.is(CURSE));
        ItemEnchantments left = kept.toImmutable();
        ItemStack result = stack;
        if (stack.is(Items.ENCHANTED_BOOK) && left.isEmpty()) {
            result = new ItemStack(Items.BOOK, stack.getCount());
        } else {
            EnchantmentHelper.setEnchantments(result, left);
        }
        result.set(DataComponents.REPAIR_COST, 0);
        return result;
    }
}
