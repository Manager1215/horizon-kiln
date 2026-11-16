package net.minecraft.world.item.enchantment;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code Enchantment}: one of the game's enchantments as its data says (kiln.GameEnchantments):
 * its name, the items it goes on, its levels and costs, the ones it excludes. Its effects are the game's: during a
 * server call Kiln asks the game (EnchantmentHelper), so a stand-in's {@code effects} are empty.
 */
public record Enchantment(Component description, EnchantmentDefinition definition, HolderSet<Enchantment> exclusiveSet,
        DataComponentMap effects) {
    public static final int MAX_LEVEL = 255;

    public HolderSet<Item> getSupportedItems() {
        return definition.supportedItems();
    }

    public int getWeight() {
        return definition.weight();
    }

    public int getAnvilCost() {
        return definition.anvilCost();
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return definition.maxLevel();
    }

    public int getMinCost(int level) {
        return definition.minCost().calculate(level);
    }

    public int getMaxCost(int level) {
        return definition.maxCost().calculate(level);
    }

    public boolean isSupportedItem(ItemStack stack) {
        return definition.supportedItems().contains(stack.typeHolder());
    }

    public boolean isPrimaryItem(ItemStack stack) {
        return isSupportedItem(stack) && definition.primaryItems().map(p -> p.contains(stack.typeHolder())).orElse(true);
    }

    public boolean canEnchant(ItemStack stack) {
        return isSupportedItem(stack);
    }

    public static boolean areCompatible(net.minecraft.core.Holder<Enchantment> a, net.minecraft.core.Holder<Enchantment> b) {
        return !a.equals(b) && !a.value().exclusiveSet().contains(b) && !b.value().exclusiveSet().contains(a);
    }

    /** The name with its level ("Sharpness V"), as the game writes it: grey, a curse's red (D-164). */
    public static Component getFullname(net.minecraft.core.Holder<Enchantment> enchantment, int level) {
        net.minecraft.network.chat.MutableComponent name = enchantment.value().description().copy();
        if (name.getStyle().getColor() == null) {
            name.withStyle(kiln.GameEnchantments.isCurse(enchantment) ? net.minecraft.ChatFormatting.RED
                    : net.minecraft.ChatFormatting.GRAY);
        }
        if (level != 1 || enchantment.value().getMaxLevel() != 1) {
            name.append(" ").append(Component.translatable("enchantment.level." + level));
        }
        return name;
    }

    /** What the game's data says of an enchantment's items, levels and costs. */
    public record EnchantmentDefinition(HolderSet<Item> supportedItems, Optional<HolderSet<Item>> primaryItems,
            int weight, int maxLevel, Cost minCost, Cost maxCost, int anvilCost, List<EquipmentSlotGroup> slots) {}

    /** A cost by level: {@code base}, and {@code perLevelAboveFirst} more for each level above the first. */
    public record Cost(int base, int perLevelAboveFirst) {
        public int calculate(int level) {
            return base + perLevelAboveFirst * (level - 1);
        }
    }
}
