package net.minecraft.world.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Kiln's stand-in for {@code ToolMaterial}: what a tier of tools is made of, with the game's own tiers' numbers
 * (D-177: a mod's sword of {@code DIAMOND} gets the diamond's attack bonus and durability in its tooltip).
 */
public record ToolMaterial(TagKey<Block> incorrectBlocksForDrops, int durability, float speed, float attackDamageBonus,
        int enchantmentValue, TagKey<Item> repairItems) {
    public static final ToolMaterial WOOD = of("wooden", 59, 2.0F, 0.0F, 15);
    public static final ToolMaterial STONE = of("stone", 131, 4.0F, 1.0F, 5);
    public static final ToolMaterial COPPER = of("copper", 190, 5.0F, 1.0F, 13);
    public static final ToolMaterial IRON = of("iron", 250, 6.0F, 2.0F, 14);
    public static final ToolMaterial DIAMOND = of("diamond", 1561, 8.0F, 3.0F, 10);
    public static final ToolMaterial GOLD = of("gold", 32, 12.0F, 0.0F, 22);
    public static final ToolMaterial NETHERITE = of("netherite", 2031, 9.0F, 4.0F, 15);

    /** A game tier: blocks {@code #incorrect_for_<name>_tool}, repairs {@code #<name>_tool_materials}. */
    private static ToolMaterial of(String name, int durability, float speed, float bonus, int enchantment) {
        return new ToolMaterial(
                TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("incorrect_for_" + name + "_tool")),
                durability, speed, bonus, enchantment,
                TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(name + "_tool_materials")));
    }
}
