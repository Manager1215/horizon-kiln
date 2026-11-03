package net.minecraft.world.level.storage.loot.providers.number.ints;

/** Kiln's stand-in for {@code ContextIntProvider}: a whole number a loot table rolls, from {@code min} to {@code max}. */
public interface ContextIntProvider {
    /** Kiln's own. */
    int kiln$min();

    /** Kiln's own. */
    int kiln$max();
}
