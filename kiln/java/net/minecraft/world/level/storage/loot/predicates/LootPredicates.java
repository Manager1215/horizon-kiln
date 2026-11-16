package net.minecraft.world.level.storage.loot.predicates;

import net.minecraft.data.worldgen.BootstrapContext;

/**
 * Kiln's stand-in for {@code LootPredicates}: the game's built-in predicates. Kiln registers none of them: the game's
 * own are read from its resources where Kiln needs them (kiln.ContextNumbers).
 */
public final class LootPredicates {
    private LootPredicates() {}

    public static void bootstrap(BootstrapContext<LootItemCondition> context) {}
}
