package net.minecraft.world.item.component;

import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Kiln's stand-in for {@code CookingFuel}, an item's {@code minecraft:cooking_fuel} component: how long it burns in a
 * furnace, and how fast it cooks there. Kiln gets a game item's from the game ({@code kiln.Recipes}).
 */
public record CookingFuel(ResolvableInt burnTime, ResolvableFloat speedMultiplier) {}
