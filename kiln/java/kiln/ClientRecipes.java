package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.ImbueRecipe;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;

/**
 * The recipes the client's mods see, as a server synchronizes them (Fabric's recipe sync, a recipe viewer's
 * recipes): the game's own, read from its prepared resources ({@code kiln.resources}'s {@code data/minecraft/recipe}),
 * the content mods' of the game's types, read from their jars, and the mods' own types ({@link Recipes#modRecipes}).
 * The game's types Kiln reads: shaped and shapeless crafting, the four cooking kinds, the stonecutter's. A world's data
 * packs aren't read: their recipes are the game's server's alone. Each world joined gets them (D-152).
 */
public final class ClientRecipes {
    private static RecipeMap recipes;

    private ClientRecipes() {}

    /** The recipes, read the first time they are asked for. */
    public static synchronized RecipeMap all() {
        if (recipes == null) {
            recipes = read();
        }
        return recipes;
    }

    /**
     * A world joined: its recipes reach the client's mods as a server's would. First Fabric's {@code
     * ClientRecipeSynchronizedEvent}, then the game's {@code ClientPacketListener.handleUpdateRecipes} (the mods'
     * mixins into it run there).
     */
    public static void synchronize(Minecraft mc) {
        RecipeMap map = all();
        try {
            ClientRecipeSynchronizedEvent.EVENT.invoker().onRecipesSynchronized(mc, SynchronizedRecipes.kiln$of(map));
        } catch (RuntimeException | LinkageError e) {
            Host.send("log", "a recipe sync listener threw " + e);
        }
        if (mc.getConnection() != null) {
            try {
                mc.getConnection().handleUpdateRecipes(
                        new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(Map.of(), null));
            } catch (RuntimeException | LinkageError e) {
                Host.send("log", "the recipes' update threw in a mod's code: " + e);
                e.printStackTrace();
            }
        }
    }

    private static RecipeMap read() {
        long started = System.nanoTime();
        Map<Identifier, RecipeHolder<?>> found = new LinkedHashMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        String resources = System.getProperty("kiln.resources", "");
        Path dir = resources.isEmpty() ? null : Path.of(resources, "data", "minecraft", "recipe");
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.walk(dir, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                    String path = dir.relativize(file).toString().replace('\\', '/');
                    Identifier id = Identifier.withDefaultNamespace(path.substring(0, path.length() - 5));
                    try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        add(found, counts, id, JsonParser.parseReader(in));
                    } catch (IOException | RuntimeException e) {
                        counts.merge("unreadable", 1, Integer::sum);
                    }
                }
            } catch (IOException e) {
                Host.send("log", "Kiln can't list the game's recipes: " + e);
            }
        }
        int game = found.size();
        for (Path jar : Recipes.modJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    Matcher m = Recipes.RECIPE_FILE.matcher(entry.getName());
                    if (!m.matches()) {
                        continue;
                    }
                    try (Reader in = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                        JsonElement json = JsonParser.parseReader(in);
                        if (json.isJsonObject() && Recipes.conditionsHold(json.getAsJsonObject())) {
                            add(found, counts, Identifier.fromNamespaceAndPath(m.group(1), m.group(2)), json);
                        }
                    } catch (IOException | RuntimeException e) {
                        counts.merge("unreadable", 1, Integer::sum);
                    }
                }
            } catch (IOException e) {
                Host.send("log", "Kiln can't read the recipes in " + jar + ": " + e);
            }
        }
        int mods = found.size() - game;
        List<RecipeHolder<?>> all = new ArrayList<>(found.values());
        for (RecipeHolder<?> own : Recipes.modRecipes()) {
            if (!found.containsKey(own.id().identifier())) {
                all.add(own);
            }
        }
        Host.send("log", "Kiln read the client's recipes in " + (System.nanoTime() - started) / 1_000_000 + " ms: "
                + game + " of the game's, " + mods + " of the game's types from the mods, "
                + (all.size() - game - mods) + " of the mods' own types " + counts);
        return RecipeMap.create(all);
    }

    private static void add(Map<Identifier, RecipeHolder<?>> found, Map<String, Integer> counts, Identifier id,
            JsonElement json) {
        if (!json.isJsonObject() || !(json.getAsJsonObject().get("type") instanceof JsonElement typeJson)) {
            return;
        }
        JsonObject o = json.getAsJsonObject();
        String type = typeJson.getAsString();
        if (!type.contains(":")) {
            type = "minecraft:" + type;
        }
        if (!type.startsWith("minecraft:")) {
            return;
        }
        DataResult<? extends Recipe<?>> recipe = switch (type) {
            case "minecraft:crafting_shaped" -> shaped(o);
            case "minecraft:crafting_shapeless" -> shapeless(o);
            case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking" ->
                    cooking(type, o);
            case "minecraft:stonecutting" -> stonecutting(o);
            case "minecraft:smithing_transform" -> smithingTransform(o);
            case "minecraft:smithing_trim" -> smithingTrim(o);
            case "minecraft:crafting_transmute" -> transmute(o);
            case "minecraft:crafting_imbue" -> imbue(o);
            case "minecraft:brewing" -> brewing(o);
            default -> ClientRecipesSpecial.read(type, o);
        };
        if (recipe == null) {
            counts.merge(type + " not read", 1, Integer::sum);
            return;
        }
        if (recipe.error().isPresent()) {
            counts.merge(type + " refused", 1, Integer::sum);
            return;
        }
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
        found.put(id, new RecipeHolder<>(key, recipe.getOrThrow()));
    }

    private static <T> DataResult<T> field(JsonObject o, String name, com.mojang.serialization.Codec<T> codec) {
        JsonElement value = o.get(name);
        return value == null ? DataResult.error(() -> "no " + name) : codec.parse(JsonOps.INSTANCE, value);
    }

    private static String string(JsonObject o, String name, String fallback) {
        JsonElement value = o.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static Recipe.CommonInfo common(JsonObject o) {
        JsonElement value = o.get("show_notification");
        return new Recipe.CommonInfo(value == null || value.getAsBoolean());
    }

    private static CraftingRecipe.CraftingBookInfo craftingBook(JsonObject o) {
        String category = string(o, "category", "misc");
        CraftingBookCategory found = CraftingBookCategory.MISC;
        for (CraftingBookCategory c : CraftingBookCategory.values()) {
            if (c.getSerializedName().equals(category)) {
                found = c;
            }
        }
        return new CraftingRecipe.CraftingBookInfo(found, string(o, "group", ""));
    }

    private static DataResult<ShapedRecipe> shaped(JsonObject o) {
        return ShapedRecipePattern.MAP_CODEC.codec().parse(JsonOps.INSTANCE, o).flatMap(pattern ->
                field(o, "result", ItemStackTemplate.CODEC).map(result ->
                        new ShapedRecipe(common(o), craftingBook(o), pattern, result)));
    }

    private static DataResult<ShapelessRecipe> shapeless(JsonObject o) {
        return field(o, "ingredients", Ingredient.CODEC.listOf()).flatMap(ingredients ->
                field(o, "result", ItemStackTemplate.CODEC).map(result ->
                        new ShapelessRecipe(common(o), craftingBook(o), result, ingredients)));
    }

    private static DataResult<AbstractCookingRecipe> cooking(String type, JsonObject o) {
        String category = string(o, "category", "misc");
        CookingBookCategory book = CookingBookCategory.MISC;
        for (CookingBookCategory c : CookingBookCategory.values()) {
            if (c.getSerializedName().equals(category)) {
                book = c;
            }
        }
        AbstractCookingRecipe.CookingBookInfo info = new AbstractCookingRecipe.CookingBookInfo(book, string(o, "group", ""));
        float experience = o.has("experience") ? o.get("experience").getAsFloat() : 0;
        int defaultTime = switch (type) {
            case "minecraft:blasting", "minecraft:smoking" -> 100;
            case "minecraft:campfire_cooking" -> 100;
            default -> 200;
        };
        int time = o.has("cookingtime") ? o.get("cookingtime").getAsInt() : defaultTime;
        return field(o, "ingredient", Ingredient.CODEC).flatMap(input ->
                field(o, "result", ItemStackTemplate.CODEC).map(result -> switch (type) {
                    case "minecraft:blasting" -> new BlastingRecipe(common(o), info, input, result, experience, time);
                    case "minecraft:smoking" -> new SmokingRecipe(common(o), info, input, result, experience, time);
                    case "minecraft:campfire_cooking" ->
                            new CampfireCookingRecipe(common(o), info, input, result, experience, time);
                    default -> new SmeltingRecipe(common(o), info, input, result, experience, time);
                }));
    }

    private static DataResult<StonecutterRecipe> stonecutting(JsonObject o) {
        return field(o, "ingredient", Ingredient.CODEC).flatMap(input ->
                field(o, "result", ItemStackTemplate.CODEC).map(result ->
                        new StonecutterRecipe(common(o), input, result)));
    }

    private static DataResult<java.util.Optional<Ingredient>> optional(JsonObject o, String name) {
        return o.has(name) ? field(o, name, Ingredient.CODEC).map(java.util.Optional::of)
                : DataResult.success(java.util.Optional.empty());
    }

    private static DataResult<SmithingTransformRecipe> smithingTransform(JsonObject o) {
        return optional(o, "template").flatMap(template -> field(o, "base", Ingredient.CODEC).flatMap(base ->
                optional(o, "addition").flatMap(addition -> field(o, "result", ItemStackTemplate.CODEC).map(result ->
                        new SmithingTransformRecipe(common(o), template, base, addition, result)))));
    }

    private static DataResult<SmithingTrimRecipe> smithingTrim(JsonObject o) {
        Identifier pattern = Identifier.tryParse(string(o, "pattern", ""));
        return field(o, "template", Ingredient.CODEC).flatMap(template -> field(o, "base", Ingredient.CODEC).flatMap(base ->
                field(o, "addition", Ingredient.CODEC).map(addition ->
                        new SmithingTrimRecipe(common(o), template, base, addition, pattern))));
    }

    private static DataResult<TransmuteRecipe> transmute(JsonObject o) {
        return field(o, "input", Ingredient.CODEC).flatMap(input -> field(o, "material", Ingredient.CODEC).flatMap(
                material -> ClientRecipesSpecial.transmuteResult(o).map(result ->
                        new TransmuteRecipe(common(o), craftingBook(o), input, material, result))));
    }

    private static DataResult<ImbueRecipe> imbue(JsonObject o) {
        return field(o, "source", Ingredient.CODEC).flatMap(source -> field(o, "material", Ingredient.CODEC).flatMap(
                material -> field(o, "result", ItemStackTemplate.CODEC).map(result ->
                        new ImbueRecipe(common(o), craftingBook(o), source, material, result))));
    }

    private static DataResult<BrewingRecipe> brewing(JsonObject o) {
        return potion(o, "input").flatMap(input -> potion(o, "reagent").flatMap(reagent ->
                field(o, "output", ItemStackTemplate.CODEC).map(output -> new BrewingRecipe(input, reagent, output))));
    }

    /** A brewing recipe's {@code {"item": ..., "potion_contents": {"potions": id | [ids] | "#tag"}}}. */
    @SuppressWarnings("unchecked")
    private static DataResult<PotionIngredient> potion(JsonObject o, String name) {
        if (!(o.get(name) instanceof JsonObject p)) {
            return DataResult.error(() -> "no " + name);
        }
        return field(p, "item", Ingredient.CODEC).flatMap(item -> {
            JsonElement potions = p.get("potion_contents") instanceof JsonObject c ? c.get("potions") : null;
            if (potions == null) {
                return DataResult.success(new PotionIngredient(item, java.util.Optional.empty()));
            }
            // The game's potions are registered when Potions is first used.
            net.minecraft.world.item.alchemy.Potions.WATER.value();
            net.minecraft.core.Registry<Potion> registry = BuiltInRegistries.POTION;
            if (potions.isJsonPrimitive() && potions.getAsString().startsWith("#")) {
                Identifier tag = Identifier.tryParse(potions.getAsString().substring(1));
                if (tag == null) {
                    return DataResult.error(() -> "not a tag: " + potions);
                }
                HolderSet<Potion> set = registry.get(TagKey.create(Registries.POTION, tag)).orElseThrow();
                return DataResult.success(new PotionIngredient(item, java.util.Optional.of(
                        new net.minecraft.core.component.predicates.PotionsPredicate(java.util.Optional.of(set)))));
            }
            List<Holder<Potion>> holders = new ArrayList<>();
            for (JsonElement id : potions.isJsonArray() ? potions.getAsJsonArray().asList() : List.of(potions)) {
                java.util.Optional<net.minecraft.core.Holder.Reference<Potion>> found =
                        registry.get(Identifier.parse(id.getAsString()));
                if (found.isEmpty()) {
                    return DataResult.error(() -> "no potion " + id);
                }
                holders.add(found.get());
            }
            return DataResult.success(new PotionIngredient(item, java.util.Optional.of(
                    new net.minecraft.core.component.predicates.PotionsPredicate(
                            java.util.Optional.of(HolderSet.direct(holders))))));
        });
    }

}
