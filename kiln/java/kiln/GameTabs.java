package kiln;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * The game's own creative tabs in {@code BuiltInRegistries.CREATIVE_MODE_TAB}, as its prepared resources list them
 * ({@code creative-inventory.json}: each tab's place, type, title, icon and items as the game's SNBT stacks), so that
 * a mod reading the registry (a recipe viewer's item list, D-152) finds them as in the game. A tab's items are read
 * when it is first built. They come first in the registry, before the mods' tabs, as the game registers its own
 * first (D-172). Horizon draws the creative inventory itself; the mods' own items in the game's tabs aren't
 * in the file (Horizon's import puts them there), and a mod that reads the tabs finds them in the item registry.
 */
public final class GameTabs {
    private static boolean registered;

    private GameTabs() {}

    /** Registers the game's tabs, once; none without the resources. A mod's tab of the same id stays. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        long started = System.nanoTime();
        String resources = System.getProperty("kiln.resources", "");
        Path file = resources.isEmpty() ? null : Path.of(resources, "creative-inventory.json");
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        int tabs = 0;
        List<Identifier> game = new ArrayList<>();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject all = JsonParser.parseReader(in).getAsJsonObject();
            for (JsonElement element : all.getAsJsonArray("tabs")) {
                JsonObject tab = element.getAsJsonObject();
                Identifier id = Identifier.parse(tab.get("id").getAsString());
                game.add(id);
                if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id)) {
                    continue;
                }
                Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab(tab));
                tabs++;
            }
            // The game's tabs before the mods' (registered at their start, before this): the game registers its own
            // first, and a recipe viewer lists the items in the registry's order of the tabs (D-172).
            if (BuiltInRegistries.CREATIVE_MODE_TAB instanceof net.minecraft.core.MappedRegistry<?> registry) {
                registry.kiln$putFirst(game);
            }
        } catch (IOException | RuntimeException e) {
            Host.send("log", "Kiln can't read the game's creative tabs: " + e);
            return;
        }
        Host.send("log", "Kiln has the game's " + tabs + " creative tabs in "
                + (System.nanoTime() - started) / 1_000_000 + " ms");
    }

    private static CreativeModeTab tab(JsonObject tab) {
        CreativeModeTab.Row row = CreativeModeTab.Row.valueOf(tab.get("row").getAsString());
        CreativeModeTab.Type type = CreativeModeTab.Type.valueOf(tab.get("type").getAsString());
        String icon = tab.has("icon") ? tab.get("icon").getAsString() : "";
        JsonArray items = tab.has("items") ? tab.getAsJsonArray("items") : new JsonArray();
        List<String> stacks = new ArrayList<>(items.size());
        items.forEach(item -> stacks.add(item.getAsString()));
        return CreativeModeTab.kiln$game(row, tab.get("column").getAsInt(), type,
                Component.literal(tab.has("title") ? tab.get("title").getAsString() : ""),
                () -> icon.isEmpty() ? ItemStack.EMPTY : stack(icon),
                (parameters, output) -> {
                    for (String snbt : stacks) {
                        ItemStack stack = stack(snbt);
                        if (!stack.isEmpty()) {
                            output.accept(stack);
                        }
                    }
                });
    }

    private static ItemStack stack(String snbt) {
        try {
            return ItemStack.kiln$fromSnbt(snbt);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
