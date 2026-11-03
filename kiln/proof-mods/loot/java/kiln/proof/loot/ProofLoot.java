package kiln.proof.loot;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * Kiln's proof of a mod's loot changes (kiln/proof.py): a nether star in every simple dungeon chest, which the game's
 * own table never has. Written against Minecraft 26.3 and Fabric's API as any mod is; built against Kiln's stand-ins.
 */
public final class ProofLoot implements ModInitializer {
    @Override
    public void onInitialize() {
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (key.equals(BuiltInLootTables.SIMPLE_DUNGEON) && source.isBuiltin()) {
                table.withPool(LootPool.lootPool()
                        .setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(Items.NETHER_STAR)));
            }
        });
    }
}
