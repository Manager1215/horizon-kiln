package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.AbstractCookingRecipe.CookingBookInfo;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * The game's recipes and fuels, for the mods' code on Kiln's server side. Horizon has them (the game's data, the
 * world's data packs, and what its import carried of the mods'), so Kiln asks for the one a mod looks for, during a
 * server call: {@code recipe <type> <item>}, answered with {@code <id> <experience> <time> <result> <count>
 * <ingredient>[,<ingredient>...]} for the cooking kinds, and {@code fuel <item>}, with {@code <ticks> <speed>}.
 */
public final class Recipes {
    private static final Identifier COOKING_FUEL = Identifier.withDefaultNamespace("cooking_fuel");

    private Recipes() {}

    /** {@code RecipeManager.getRecipeFor}: the game's recipe of {@code type} that {@code input} matches. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> find(RecipeType<T> type, I input) {
        Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (id == null || !(input instanceof SingleRecipeInput single) || !cooking(type)) {
            Missing.called("recipes of type " + id + " (" + type + ")");
            return Optional.empty();
        }
        if (single.item().isEmpty() || !Server.inCall()) {
            return Optional.empty();
        }
        String answer = Server.ask("recipe", id.toString(), single.item().getItem().kiln$id().toString());
        if (answer.isBlank()) {
            return Optional.empty();
        }
        String[] f = answer.trim().split(" ");
        Ingredient ingredient = Ingredient.of(Arrays.stream(f[5].split(",")).map(Item::kiln$byId));
        ItemStackTemplate result = new ItemStackTemplate(Item.kiln$byId(f[3]), Integer.parseInt(f[4]));
        AbstractCookingRecipe.CommonInfo common = new Recipe.CommonInfo(true);
        CookingBookInfo book = new CookingBookInfo(CookingBookCategory.MISC, "");
        float experience = Float.parseFloat(f[1]);
        int time = Integer.parseInt(f[2]);
        AbstractCookingRecipe recipe;
        if (type == RecipeType.BLASTING) {
            recipe = new BlastingRecipe(common, book, ingredient, result, experience, time);
        } else if (type == RecipeType.SMOKING) {
            recipe = new SmokingRecipe(common, book, ingredient, result, experience, time);
        } else if (type == RecipeType.CAMPFIRE_COOKING) {
            recipe = new CampfireCookingRecipe(common, book, ingredient, result, experience, time);
        } else {
            recipe = new SmeltingRecipe(common, book, ingredient, result, experience, time);
        }
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.parse(f[0]));
        return Optional.of((RecipeHolder<T>) (RecipeHolder) new RecipeHolder<>(key, recipe));
    }

    /** The mods' own recipes (a machine's kinds), read from their jars the first time a mod's code looks. */
    private static List<RecipeHolder<?>> modRecipes;

    /**
     * The recipes of the mods' own types ({@code data/<ns>/recipe/...json} in each mod's jar whose type a mod
     * registered a serializer for), each read with its mod's own codec, as the game reads a data pack. The game's own
     * types are the game's (asked one at a time, {@code find}). A recipe whose load conditions don't hold, or that its
     * codec refuses (an item of a mod that isn't there), is left out, as in the game; the log says how many.
     */
    public static synchronized Collection<RecipeHolder<?>> modRecipes() {
        if (modRecipes == null) {
            modRecipes = Collections.unmodifiableList(readModRecipes());
        }
        return modRecipes;
    }

    /** The registries' sizes the mods' recipes were last read for: recipe serializers, items. */
    private static long readFor = -1;

    /**
     * After a mod's entrypoints ran ({@code Server.announce}): when the mods registered recipe serializers or items
     * since, their recipes are read again, now, while Java starts, rather than in a server call that must answer in
     * time.
     */
    static synchronized void prepare() {
        Registry<?> serializers = serializers();
        int kinds = serializers == null ? 0 : serializers.keySet().size();
        long now = (long) kinds << 32 | BuiltInRegistries.ITEM.keySet().size();
        if (kinds == 0 || now == readFor) {
            return;
        }
        readFor = now;
        modRecipes = null;
        modRecipes();
    }

    /** An item by its id, for a recipe's codec: an item of a mod that isn't there is an error. */
    public static DataResult<Item> item(String id) {
        Identifier name = Identifier.tryParse(id);
        if (name == null) {
            return DataResult.error(() -> "not an item id: " + id);
        }
        if (!name.getNamespace().equals("minecraft") && !BuiltInRegistries.ITEM.containsKey(name)) {
            return DataResult.error(() -> "no item " + id);
        }
        return DataResult.success(Item.kiln$byId(name.toString()));
    }

    private static List<RecipeHolder<?>> readModRecipes() {
        long started = System.nanoTime();
        Registry<?> serializers = serializers();
        List<RecipeHolder<?>> found = new ArrayList<>();
        Set<Identifier> ids = new HashSet<>();
        Map<String, Integer> read = new TreeMap<>();
        Map<String, String> refused = new TreeMap<>();
        int conditioned = 0;
        for (Path jar : modJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    Matcher m = RECIPE_FILE.matcher(entry.getName());
                    if (!m.matches()) {
                        continue;
                    }
                    JsonElement json;
                    try (Reader r = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                        json = JsonParser.parseReader(r);
                    } catch (RuntimeException e) {
                        continue;
                    }
                    if (!json.isJsonObject() || !json.getAsJsonObject().has("type")) {
                        continue;
                    }
                    String type = json.getAsJsonObject().get("type").getAsString();
                    Identifier typeId = Identifier.tryParse(type);
                    if (typeId == null || typeId.getNamespace().equals("minecraft")) {
                        continue;
                    }
                    if (!conditionsHold(json.getAsJsonObject())) {
                        conditioned++;
                        continue;
                    }
                    Identifier id = Identifier.fromNamespaceAndPath(m.group(1), m.group(2));
                    MapCodec<?> codec = serializers == null ? null : codecOf(serializers.getValue(typeId));
                    if (codec == null || !ids.add(id)) {
                        continue;
                    }
                    DataResult<?> recipe;
                    try {
                        recipe = codec.codec().parse(JsonOps.INSTANCE, json);
                    } catch (RuntimeException e) {
                        recipe = DataResult.error(e::toString);
                    }
                    if (recipe.error().isPresent()) {
                        refused.merge(type, id + ": " + recipe.error().get().message(), (a, b) -> a);
                        read.merge(type + " refused", 1, Integer::sum);
                        continue;
                    }
                    if (!(recipe.getOrThrow() instanceof Recipe<?> made)) {
                        continue;
                    }
                    ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                    found.add(new RecipeHolder<>(key, made));
                    read.merge(type, 1, Integer::sum);
                }
            } catch (IOException e) {
                Host.send("log", "Kiln can't read the recipes in " + jar + ": " + e);
            }
        }
        Host.send("log", "Kiln read " + found.size() + " recipes of the mods' own types in "
                + (System.nanoTime() - started) / 1_000_000 + " ms " + read
                + (conditioned > 0 ? ", " + conditioned + " left out by their load conditions" : "")
                + (refused.isEmpty() ? "" : "; first refused of each type: " + refused));
        return found;
    }

    /**
     * The mods' recipe serializers ({@code BuiltInRegistries.RECIPE_SERIALIZER}), whose classes are generated: reached
     * by name, as a hand-written stand-in may name only hand-written ones.
     */
    private static Registry<?> serializers() {
        try {
            return (Registry<?>) BuiltInRegistries.class.getField("RECIPE_SERIALIZER").get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            Host.send("log", "Kiln has no recipe serializers to read the mods' recipes with: " + e);
            return null;
        }
    }

    /** A recipe serializer's codec ({@code RecipeSerializer.codec()}), or null. */
    private static MapCodec<?> codecOf(Object serializer) {
        if (serializer == null) {
            return null;
        }
        try {
            return serializer.getClass().getMethod("codec").invoke(serializer) instanceof MapCodec<?> codec ? codec : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static final Pattern RECIPE_FILE = Pattern.compile("data/([a-z0-9_.-]+)/recipe/([a-z0-9_./-]+)\\.json");

    /** The jars of the mods on Java's class path, as their fabric.mod.json shows them. */
    private static List<Path> modJars() {
        List<Path> jars = new ArrayList<>();
        try {
            for (URL url : Collections.list(Recipes.class.getClassLoader().getResources("fabric.mod.json"))) {
                if (url.openConnection() instanceof JarURLConnection connection) {
                    Path jar = Path.of(connection.getJarFileURL().toURI());
                    if (!jars.contains(jar)) {
                        jars.add(jar);
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            Host.send("log", "Kiln can't list the mods' jars: " + e);
        }
        return jars;
    }

    /**
     * Fabric's load conditions ({@code fabric:load_conditions}): every one must hold. Kiln knows which mods are
     * there ({@code fabric:all_mods_loaded}, {@code fabric:any_mods_loaded}, {@code fabric:not}, {@code fabric:and},
     * {@code fabric:or}); a tag's being filled is the game's to know, taken as so; another condition leaves the
     * recipe out.
     */
    private static boolean conditionsHold(JsonObject recipe) {
        if (!recipe.has("fabric:load_conditions")) {
            return true;
        }
        for (JsonElement condition : recipe.getAsJsonArray("fabric:load_conditions")) {
            if (!holds(condition.getAsJsonObject())) {
                return false;
            }
        }
        return true;
    }

    private static boolean holds(JsonObject condition) {
        String kind = condition.has("condition") ? condition.get("condition").getAsString() : "";
        return switch (kind) {
            case "fabric:all_mods_loaded" -> stringsOf(condition, "values").stream().allMatch(Recipes::modLoaded);
            case "fabric:any_mods_loaded" -> stringsOf(condition, "values").stream().anyMatch(Recipes::modLoaded);
            case "fabric:not" -> !holds(condition.getAsJsonObject("value"));
            case "fabric:and" -> condition.getAsJsonArray("values").asList().stream()
                    .allMatch(c -> holds(c.getAsJsonObject()));
            case "fabric:or" -> condition.getAsJsonArray("values").asList().stream()
                    .anyMatch(c -> holds(c.getAsJsonObject()));
            case "fabric:tags_populated" -> true;
            default -> false;
        };
    }

    private static List<String> stringsOf(JsonObject json, String key) {
        List<String> values = new ArrayList<>();
        if (json.has(key)) {
            json.getAsJsonArray(key).forEach(v -> values.add(v.getAsString()));
        }
        return values;
    }

    private static boolean modLoaded(String id) {
        return id.equals("minecraft") || Mods.all().stream().anyMatch(m -> m.getMetadata().getId().equals(id));
    }

    private static boolean cooking(RecipeType<?> type) {
        return type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING
                || type == RecipeType.CAMPFIRE_COOKING;
    }

    /**
     * A component of {@code item} itself that a stack doesn't set and Kiln can't know from the game's names alone:
     * a game item's fuel ({@code minecraft:cooking_fuel}), asked of the game during a call. Null otherwise.
     */
    @SuppressWarnings("unchecked")
    public static <T> T component(Item item, DataComponentType<? extends T> type) {
        if (item == null || !COOKING_FUEL.equals(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)) || !Server.inCall()) {
            return null;
        }
        String[] f = Server.ask("fuel", item.kiln$id().toString()).trim().split(" ");
        if (f.length < 2 || Integer.parseInt(f[0]) <= 0) {
            return null;
        }
        return (T) new CookingFuel(ResolvableInt.kiln$exactly(Integer.parseInt(f[0])),
                ResolvableFloat.kiln$exactly(Float.parseFloat(f[1])));
    }
}
