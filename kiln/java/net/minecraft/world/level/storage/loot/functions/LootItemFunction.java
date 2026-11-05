package net.minecraft.world.level.storage.loot.functions;

/** Kiln's stand-in for {@code LootItemFunction}: what changes a dropped stack (its count, damage). */
public interface LootItemFunction {
    /** How a function is made in a loot table's builder. */
    interface Builder {
        LootItemFunction build();
    }
}
