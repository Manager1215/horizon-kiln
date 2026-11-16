package net.fabricmc.fabric.api.item.v1;

/**
 * Kiln's stand-in for Fabric's {@code EnchantingContext}: where an enchantment is put on an item. {@code PRIMARY} is an
 * enchanting table (the enchantment's primary items), {@code ACCEPTABLE} an anvil or a command (its supported items).
 */
public enum EnchantingContext {
    PRIMARY,
    ACCEPTABLE
}
