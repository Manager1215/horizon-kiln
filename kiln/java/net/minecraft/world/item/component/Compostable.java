package net.minecraft.world.item.component;

import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Kiln's stand-in for {@code Compostable}, an item's {@code minecraft:compostable} component: how many layers it adds
 * to a composter, a number or a reference to the game's {@code context_int_provider} files. Kiln reads a game item's
 * from {@code items.json} ({@code kiln.ClientComponents}).
 */
public record Compostable(ResolvableInt layers) {}
