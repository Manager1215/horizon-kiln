package kiln;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

/**
 * The mods' changes to the game's loot tables: Fabric fires {@code LootTableEvents.MODIFY} for every table as it
 * loads; Kiln fires it for each of the game's own tables ({@code BuiltInLootTables}) and the ones named, on an empty
 * builder, and answers {@code loot <id> <pools as JSON>} for each table a mod added pools to, in the game's loot table
 * format. Horizon adds them to the table as it loads it (Kiln's loot.rs and loot_table.rs).
 */
final class Loot {
    private static final HolderLookup.Provider REGISTRIES = new HolderLookup.Provider() {};

    private Loot() {}

    /** The {@code loot [<id>...]} command. */
    static void modify(String[] f) {
        Set<String> ids = new TreeSet<>(List.of(f).subList(1, f.length));
        ids.addAll(builtIn());
        for (String id : ids) {
            LootTable.Builder table = LootTable.lootTable();
            try {
                LootTableEvents.MODIFY.invoker().modifyLootTable(
                        ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(id)), table,
                        LootTableSource.VANILLA, REGISTRIES);
            } catch (RuntimeException | LinkageError e) {
                Host.send("log", "a mod's change to loot table " + id + " failed: " + e);
                continue;
            }
            List<LootPool> pools = table.kiln$pools();
            if (!pools.isEmpty()) {
                JsonArray json = new JsonArray();
                pools.forEach(p -> json.add(pool(p)));
                Host.send("loot", id, json.toString());
            }
        }
    }

    /** The ids of BuiltInLootTables' keys. */
    private static Set<String> builtIn() {
        Set<String> ids = new TreeSet<>();
        for (Field field : BuiltInLootTables.class.getFields()) {
            try {
                if (field.get(null) instanceof ResourceKey<?> key) {
                    ids.add(key.identifier().toString());
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return ids;
    }

    private static JsonObject pool(LootPool pool) {
        JsonObject json = new JsonObject();
        json.add("rolls", number(pool.kiln$rolls()));
        JsonArray entries = new JsonArray();
        for (LootPoolEntryContainer entry : pool.kiln$entries()) {
            if (entry instanceof LootItem item) {
                JsonObject e = new JsonObject();
                e.addProperty("type", "minecraft:item");
                e.addProperty("name", item.kiln$item().kiln$id().toString());
                if (item.kiln$weight() != 1) {
                    e.addProperty("weight", item.kiln$weight());
                }
                entries.add(e);
            } else {
                Host.send("log", "a mod's loot entry Kiln can't write yet: " + entry.getClass().getName());
            }
        }
        json.add("entries", entries);
        return json;
    }

    /** A constant, or a uniform range, as the loot table format writes it. */
    private static JsonElement number(ContextIntProvider n) {
        if (n.kiln$min() == n.kiln$max()) {
            return new JsonPrimitive(n.kiln$min());
        }
        JsonObject range = new JsonObject();
        range.addProperty("type", "minecraft:uniform");
        range.addProperty("min", n.kiln$min());
        range.addProperty("max", n.kiln$max());
        return range;
    }
}
