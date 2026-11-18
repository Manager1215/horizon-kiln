package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.DecoratedPotRecipe;
import net.minecraft.world.item.crafting.DyeRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.item.crafting.FireworkStarFadeRecipe;
import net.minecraft.world.item.crafting.FireworkStarRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.MapExtendingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import net.minecraft.world.item.crafting.TransmuteResult;

/**
 * The game's special crafting recipes as the client reads them ({@link ClientRecipes}): the dyed armor ({@code
 * minecraft:crafting_dye}), the decorated pot and the {@code crafting_special_*} kinds (a banner copied, a book cloned,
 * a map zoomed out, an item repaired, a shield decorated, fireworks). As in the game, only the dye recipes have a
 * display; a recipe viewer shows the firework and shield ones by their classes (D-157).
 */
public final class ClientRecipesSpecial {
    private ClientRecipesSpecial() {}

    /**
     * The recipe {@code o} of {@code type} (with its namespace) is, or null when {@code type} isn't one of these: a
     * refused one is an error.
     */
    public static DataResult<? extends Recipe<?>> read(String type, JsonObject o) {
        return switch (type) {
            case "minecraft:crafting_dye" -> ingredient(o, "target").flatMap(target -> ingredient(o, "dye")
                    .flatMap(dye -> result(o).map(result -> new DyeRecipe(common(o), book(o), target, dye, result))));
            case "minecraft:crafting_decorated_pot" -> ingredient(o, "back").flatMap(back -> ingredient(o, "left")
                    .flatMap(left -> ingredient(o, "right").flatMap(right -> ingredient(o, "front")
                            .flatMap(front -> result(o).map(result ->
                                    new DecoratedPotRecipe(back, left, right, front, result))))));
            case "minecraft:crafting_special_bannerduplicate" -> ingredient(o, "banner").flatMap(banner ->
                    result(o).map(result -> new BannerDuplicateRecipe(banner, result)));
            case "minecraft:crafting_special_bookcloning" -> ingredient(o, "source").flatMap(source ->
                    ingredient(o, "material").flatMap(material ->
                            bounds(o, "allowed_generations", BookCloningRecipe.DEFAULT_GENERATIONS).flatMap(
                                    generations -> result(o).map(result ->
                                            new BookCloningRecipe(source, material, generations, result)))));
            case "minecraft:crafting_special_mapextending" -> ingredient(o, "map").flatMap(map ->
                    ingredient(o, "material").flatMap(material ->
                            transmuteResult(o).map(result -> new MapExtendingRecipe(map, material, result))));
            case "minecraft:crafting_special_repairitem" -> DataResult.success(RepairItemRecipe.INSTANCE);
            case "minecraft:crafting_special_shielddecoration" -> ingredient(o, "banner").flatMap(banner ->
                    ingredient(o, "target").flatMap(target ->
                            result(o).map(result -> new ShieldDecorationRecipe(banner, target, result))));
            case "minecraft:crafting_special_firework_rocket" -> ingredient(o, "shell").flatMap(shell ->
                    ingredient(o, "fuel").flatMap(fuel -> ingredient(o, "star").flatMap(star ->
                            result(o).map(result -> new FireworkRocketRecipe(shell, fuel, star, result)))));
            case "minecraft:crafting_special_firework_star_fade" -> ingredient(o, "target").flatMap(target ->
                    ingredient(o, "dye").flatMap(dye ->
                            result(o).map(result -> new FireworkStarFadeRecipe(target, dye, result))));
            case "minecraft:crafting_special_firework_star" -> fireworkStar(o);
            default -> null;
        };
    }

    /** A range the recipe names ({@code material_count}: 3, or {"min": 1, "max": 8}), else {@code otherwise}. */
    static DataResult<net.minecraft.advancements.predicates.MinMaxBounds.Ints> bounds(JsonObject o, String key,
            net.minecraft.advancements.predicates.MinMaxBounds.Ints otherwise) {
        return o.has(key)
                ? net.minecraft.advancements.predicates.MinMaxBounds.Ints.CODEC.parse(
                        com.mojang.serialization.JsonOps.INSTANCE, o.get(key))
                : DataResult.success(otherwise);
    }

    private static DataResult<FireworkStarRecipe> fireworkStar(JsonObject o) {
        if (!(o.get("shapes") instanceof JsonObject shapesJson)) {
            return DataResult.error(() -> "no shapes");
        }
        Map<FireworkExplosion.Shape, Ingredient> shapes = new EnumMap<>(FireworkExplosion.Shape.class);
        for (Map.Entry<String, JsonElement> entry : shapesJson.entrySet()) {
            DataResult<FireworkExplosion.Shape> shape =
                    FireworkExplosion.Shape.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(entry.getKey()));
            DataResult<Ingredient> ingredient = Ingredient.CODEC.parse(JsonOps.INSTANCE, entry.getValue());
            if (shape.error().isPresent() || ingredient.error().isPresent()) {
                return DataResult.error(() -> "a shape that isn't one: " + entry.getKey());
            }
            shapes.put(shape.getOrThrow(), ingredient.getOrThrow());
        }
        return ingredient(o, "trail").flatMap(trail -> ingredient(o, "twinkle").flatMap(twinkle ->
                ingredient(o, "fuel").flatMap(fuel -> ingredient(o, "dye").flatMap(dye -> result(o).map(result ->
                        new FireworkStarRecipe(shapes, trail, twinkle, fuel, dye, result))))));
    }

    private static <T> DataResult<T> field(JsonObject o, String name, Codec<T> codec) {
        JsonElement value = o.get(name);
        return value == null ? DataResult.error(() -> "no " + name) : codec.parse(JsonOps.INSTANCE, value);
    }

    private static DataResult<Ingredient> ingredient(JsonObject o, String name) {
        return field(o, name, Ingredient.CODEC);
    }

    private static DataResult<ItemStackTemplate> result(JsonObject o) {
        return field(o, "result", ItemStackTemplate.CODEC);
    }

    /** A {@code TransmuteResult}: {@code {}} keeps the input's item, else an {@code id}, a count and components. */
    static DataResult<TransmuteResult> transmuteResult(JsonObject o) {
        if (!(o.get("result") instanceof JsonObject r)) {
            return DataResult.error(() -> "no result");
        }
        if (!r.has("id")) {
            int count = r.has("count") ? r.get("count").getAsInt() : 1;
            return DataResult.success(count == 1 && !r.has("components") ? TransmuteResult.KEEP_INPUT_ITEM
                    : new TransmuteResult(Optional.empty(), count, DataComponentPatch.EMPTY));
        }
        return ItemStackTemplate.CODEC.parse(JsonOps.INSTANCE, r).map(TransmuteResult::fromTemplate);
    }

    private static Recipe.CommonInfo common(JsonObject o) {
        JsonElement value = o.get("show_notification");
        return new Recipe.CommonInfo(value == null || value.getAsBoolean());
    }

    private static CraftingRecipe.CraftingBookInfo book(JsonObject o) {
        String category = o.get("category") instanceof JsonElement c && c.isJsonPrimitive() ? c.getAsString() : "misc";
        String group = o.get("group") instanceof JsonElement g && g.isJsonPrimitive() ? g.getAsString() : "";
        CraftingBookCategory found = CraftingBookCategory.MISC;
        for (CraftingBookCategory c : CraftingBookCategory.values()) {
            if (c.getSerializedName().equals(category)) {
                found = c;
            }
        }
        return new CraftingRecipe.CraftingBookInfo(found, group);
    }
}
