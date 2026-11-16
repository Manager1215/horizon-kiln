package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * What the game's own items are, read from its prepared resources (the {@code kiln.resources} folder) rather than
 * asked of the running game: an item tag's items ({@code data/<namespace>/tags/item/<path>.json}, nested tags
 * followed) and the items that burn in a furnace ({@code items.json}'s {@code minecraft:cooking_fuel}). For showing
 * a recipe (a recipe viewer's slots), not for deciding one: a world's data packs and the content mods' tags aren't in
 * them, and {@link Tags} asks the game when a server call is on.
 */
public final class GameItems {
    private static final Map<Identifier, List<Item>> TAGS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> TAG_IDS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> TAG_SETS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, List<Identifier>>> BY_ID = new ConcurrentHashMap<>();
    private static volatile List<Item> fuels;

    private GameItems() {}

    /** The items in the game's item tag {@code tag}, in its file's order; none without the resources or the tag. */
    public static List<Item> tag(Identifier tag) {
        return TAGS.computeIfAbsent(tag, t -> {
            List<Item> items = new ArrayList<>();
            for (String id : tagIds("item", t)) {
                Recipes.item(id).result().ifPresent(items::add);
            }
            return List.copyOf(items);
        });
    }

    /**
     * The ids in the game's tag {@code tag} of the registry whose tags are in {@code folder} ({@code item},
     * {@code block}...), nested tags followed; none without the resources or the tag.
     */
    public static List<String> tagIds(String folder, Identifier tag) {
        return TAG_IDS.computeIfAbsent(folder + " " + tag, k -> {
            Set<String> ids = new LinkedHashSet<>();
            collect(folder, tag, ids, new HashSet<>());
            return List.copyOf(ids);
        });
    }

    /** Whether {@code id} is in the game's tag {@code tag} of {@code folder}'s registry, nested tags followed. */
    public static boolean inTag(String folder, Identifier tag, String id) {
        return TAG_SETS.computeIfAbsent(folder + " " + tag, k -> Set.copyOf(tagIds(folder, tag))).contains(id);
    }

    /**
     * The game's tags of {@code folder}'s registry that {@code id} is in, nested tags followed: every tag file under
     * {@code data/<namespace>/tags/<folder>/} read once, the first time one is asked for.
     */
    public static List<Identifier> tagsOf(String folder, String id) {
        return BY_ID.computeIfAbsent(folder, GameItems::index).getOrDefault(id, List.of());
    }

    private static Map<String, List<Identifier>> index(String folder) {
        Map<String, List<Identifier>> byId = new java.util.HashMap<>();
        for (Identifier tag : GameTags.names(folder)) {
            for (String member : tagIds(folder, tag)) {
                byId.computeIfAbsent(member, m -> new ArrayList<>()).add(tag);
            }
        }
        return byId;
    }

    private static void collect(String folder, Identifier tag, Set<String> ids, Set<Identifier> seen) {
        if (!seen.add(tag)) {
            return;
        }
        String resources = System.getProperty("kiln.resources", "");
        String name = "data/" + tag.getNamespace() + "/tags/" + folder + "/" + tag.getPath() + ".json";
        // The game's file and Fabric API's (D-173): the game merges a tag's files from every pack.
        Path file = resources.isEmpty() ? null : Path.of(resources, name);
        if (file != null && Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                read(folder, tag, in, ids, seen);
            } catch (IOException | RuntimeException e) {
                System.err.println("Kiln: the game's " + folder + " tag " + tag + ": " + e);
            }
        }
        try (Reader in = FabricData.open(name)) {
            if (in != null) {
                read(folder, tag, in, ids, seen);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Kiln: Fabric API's " + folder + " tag " + tag + ": " + e);
        }
    }

    private static void read(String folder, Identifier tag, Reader in, Set<String> ids, Set<Identifier> seen) {
        JsonElement values = JsonParser.parseReader(in).getAsJsonObject().get("values");
        if (values == null || !values.isJsonArray()) {
            return;
        }
        for (JsonElement value : values.getAsJsonArray()) {
            String id = value.isJsonObject() && value.getAsJsonObject().get("id") != null
                    ? value.getAsJsonObject().get("id").getAsString()
                    : value.isJsonPrimitive() ? value.getAsString() : null;
            if (id == null) {
                continue;
            }
            if (id.startsWith("#")) {
                Identifier inner = Identifier.tryParse(id.substring(1));
                if (inner != null) {
                    collect(folder, inner, ids, seen);
                }
            } else {
                ids.add(id.contains(":") ? id : "minecraft:" + id);
            }
        }
    }

    private static volatile JsonObject items;

    /** The game item {@code id}'s component {@code component} as {@code items.json} has it; null without it. */
    public static JsonElement itemComponent(String id, String component) {
        JsonObject item = itemComponents(id);
        return item == null ? null : item.get(component);
    }

    /** The game item {@code id}'s components as {@code items.json} has them, by id; null for one it hasn't. */
    public static JsonObject itemComponents(String id) {
        JsonObject all = items;
        if (all == null) {
            all = new JsonObject();
            String resources = System.getProperty("kiln.resources", "");
            Path file = resources.isEmpty() ? null : Path.of(resources, "items.json");
            if (file != null && Files.isRegularFile(file)) {
                try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    all = JsonParser.parseReader(in).getAsJsonObject();
                } catch (IOException | RuntimeException e) {
                    System.err.println("Kiln: the game's items: " + e);
                }
            }
            items = all;
        }
        return all.get(id) instanceof JsonObject item ? item : null;
    }

    /** The game's items that burn in a furnace, in {@code items.json}'s order; none without the resources. */
    public static List<Item> fuels() {
        List<Item> known = fuels;
        if (known != null) {
            return known;
        }
        List<Item> items = new ArrayList<>();
        String resources = System.getProperty("kiln.resources", "");
        Path file = resources.isEmpty() ? null : Path.of(resources, "items.json");
        if (file != null && Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject all = JsonParser.parseReader(in).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : all.entrySet()) {
                    if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("minecraft:cooking_fuel")) {
                        Recipes.item(entry.getKey()).result().ifPresent(items::add);
                    }
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("Kiln: the game's fuels: " + e);
            }
        }
        fuels = List.copyOf(items);
        return fuels;
    }
}
