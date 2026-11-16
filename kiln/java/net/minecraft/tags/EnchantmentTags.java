package net.minecraft.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

/** Kiln's stand-in for {@code EnchantmentTags}: the game's enchantment tags mods name. */
public final class EnchantmentTags {
    public static final TagKey<Enchantment> CURSE = create("curse");
    public static final TagKey<Enchantment> TOOLTIP_ORDER = create("tooltip_order");
    public static final TagKey<Enchantment> IN_ENCHANTING_TABLE = create("in_enchanting_table");
    public static final TagKey<Enchantment> NON_TREASURE = create("non_treasure");
    public static final TagKey<Enchantment> TREASURE = create("treasure");
    public static final TagKey<Enchantment> TRADEABLE = create("tradeable");
    public static final TagKey<Enchantment> DOUBLE_TRADE_PRICE = create("double_trade_price");
    public static final TagKey<Enchantment> ON_RANDOM_LOOT = create("on_random_loot");
    public static final TagKey<Enchantment> SMELTS_LOOT = create("smelts_loot");

    private EnchantmentTags() {}

    private static TagKey<Enchantment> create(String name) {
        return TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(name));
    }
}
