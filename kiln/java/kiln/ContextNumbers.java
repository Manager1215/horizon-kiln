package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * The game's numbers that depend on a context ({@code data/<ns>/context_int_provider/}, {@code context_float_provider/}:
 * a fuel's burn time, its cooking speed), worked out from its prepared resources: numbers, references to other files,
 * {@code minecraft:div} and {@code minecraft:conditional} on a block predicate ({@code minecraft:match_block}, the
 * context's block state). Other kinds give the caller's fallback. For showing a game item's fuel on the client (a
 * recipe viewer); during a server call Kiln asks the game ({@link Recipes#component}).
 */
public final class ContextNumbers {
    private static final Map<String, Optional<JsonElement>> FILES = new ConcurrentHashMap<>();
    private static final Map<String, Optional<CookingFuel>> FUELS = new ConcurrentHashMap<>();

    private ContextNumbers() {}

    /** A game item's {@code minecraft:cooking_fuel}, from {@code items.json}; none for an item that doesn't burn. */
    public static CookingFuel cookingFuel(Item item) {
        return FUELS.computeIfAbsent(item.kiln$id().toString(), id -> {
            JsonElement fuel = GameItems.itemComponent(id, "minecraft:cooking_fuel");
            if (!(fuel instanceof JsonObject o) || !o.has("burn_time")) {
                return Optional.empty();
            }
            JsonElement burn = o.get("burn_time");
            JsonElement speed = o.get("speed_multiplier");
            ResolvableInt time = (context, fallback) -> {
                double value = eval(burn, "context_int_provider", context, 0);
                return Double.isNaN(value) ? fallback : (int) value;
            };
            ResolvableFloat multiplier = (context, fallback) -> {
                double value = speed == null ? Double.NaN : eval(speed, "context_float_provider", context, 0);
                return Double.isNaN(value) ? fallback : (float) value;
            };
            return Optional.of(new CookingFuel(time, multiplier));
        }).orElse(null);
    }

    /** {@code json} worked out in {@code context}; NaN for what Kiln can't work out. */
    static double eval(JsonElement json, String folder, LootContext context, int depth) {
        if (json == null || depth > 16) {
            return Double.NaN;
        }
        if (json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isNumber()) {
                return json.getAsDouble();
            }
            return file(folder, json.getAsString()).map(f -> eval(f, folder, context, depth + 1)).orElse(Double.NaN);
        }
        if (!(json instanceof JsonObject o) || !o.has("type")) {
            return Double.NaN;
        }
        switch (o.get("type").getAsString().replace("minecraft:", "")) {
            case "div" -> {
                double left = eval(o.get("left"), folder, context, depth + 1);
                double right = eval(o.get("right"), folder, context, depth + 1);
                if (Double.isNaN(left) || Double.isNaN(right) || right == 0) {
                    return Double.NaN;
                }
                return folder.equals("context_int_provider") ? Math.floor(left / right) : left / right;
            }
            case "conditional" -> {
                Boolean test = test(o.get("condition"), context, depth + 1);
                return test == null ? Double.NaN
                        : eval(o.get(test ? "on_true" : "on_false"), folder, context, depth + 1);
            }
            case "constant" -> {
                return eval(o.get("value"), folder, context, depth + 1);
            }
            default -> {
                return Double.NaN;
            }
        }
    }

    /** A block predicate ({@code data/<ns>/predicate/}) on the context's block state; null if Kiln can't tell. */
    private static Boolean test(JsonElement json, LootContext context, int depth) {
        if (json == null || depth > 16) {
            return null;
        }
        if (json.isJsonPrimitive()) {
            return file("predicate", json.getAsString()).map(f -> test(f, context, depth + 1)).orElse(null);
        }
        if (!(json instanceof JsonObject o) || !o.has("type")) {
            return null;
        }
        if (!o.get("type").getAsString().replace("minecraft:", "").equals("match_block")) {
            return null;
        }
        BlockState state = context == null ? null : context.kiln$parameter(LootContextParams.BLOCK_STATE);
        if (state == null) {
            return false;
        }
        String block = state.getBlock().kiln$id().toString();
        JsonElement blocks = o.get("blocks");
        if (blocks == null) {
            return true;
        }
        for (JsonElement b : blocks.isJsonArray() ? blocks.getAsJsonArray().asList() : java.util.List.of(blocks)) {
            String id = b.getAsString();
            if (id.startsWith("#") ? GameItems.inTag("block", Identifier.parse(id.substring(1)), block)
                    : Identifier.parse(id).toString().equals(block)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<JsonElement> file(String folder, String reference) {
        Identifier id = Identifier.tryParse(reference);
        if (id == null) {
            return Optional.empty();
        }
        return FILES.computeIfAbsent(folder + " " + id, k -> {
            String resources = System.getProperty("kiln.resources", "");
            Path path = resources.isEmpty() ? null
                    : Path.of(resources, "data", id.getNamespace(), folder, id.getPath() + ".json");
            if (path == null || !Files.isRegularFile(path)) {
                return Optional.empty();
            }
            try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return Optional.of(JsonParser.parseReader(in));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        });
    }
}
