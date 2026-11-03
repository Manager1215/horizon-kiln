package net.fabricmc.fabric.api.loot.v3;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Kiln's stand-in for Fabric's {@code LootTableEvents}: a mod adds to the game's loot tables as they load. Kiln
 * keeps the listeners; turning what they add into loot Horizon reads is still to come (internal/server-side.md).
 */
public final class LootTableEvents {
    public static final Event<Modify> MODIFY = Event.kiln$of(listeners -> (key, table, source, registries) -> {
        for (Modify l : listeners) {
            l.modifyLootTable(key, table, source, registries);
        }
    });

    private LootTableEvents() {}

    @FunctionalInterface
    public interface Modify {
        void modifyLootTable(ResourceKey<LootTable> key, LootTable.Builder table, LootTableSource source,
                HolderLookup.Provider registries);
    }
}
