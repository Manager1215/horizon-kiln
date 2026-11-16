package net.minecraft.world.item.enchantment;

/**
 * Kiln's stand-in for {@code Enchantable}, an item's {@code minecraft:enchantable} component: that an enchanting table
 * takes it, and how well it enchants. Kiln reads a game item's from {@code items.json} ({@code kiln.ClientComponents}).
 */
public record Enchantable(int value) {}
