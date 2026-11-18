import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

/**
 * The game's special crafting recipes crafted through Kiln's stand-ins (D-197), for kiln/tests/special_recipes.rs:
 * each recipe as {@code kiln.ClientRecipes} reads it from the game's resources, given a grid, must match or not and
 * make what the game makes (a dyed helmet's color, a star's explosion, a rocket's flight, a decorated shield, a
 * banner's and a book's copies and what stays in the grid, a repaired tool's durability and curses, maps cloned and
 * zoomed out, a decorated pot). A line on stdout per case, "ok ..." or "FAILED ..."; it exits 1 if any failed. Runs as
 * a source file with kiln-runtime.jar on the class path and {@code -Dkiln.resources}.
 */
public class SpecialRecipes {
    static RecipeMap recipes;
    static int failed;
    static int passed;

    public static void main(String[] args) throws Exception {
        var out = Class.forName("kiln.Host").getDeclaredField("out");
        out.setAccessible(true);
        out.set(null, new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        recipes = (RecipeMap) Class.forName("kiln.ClientRecipes").getMethod("all").invoke(null);

        // Dyed leather: one dye keeps its color; two mix; a dyed helmet mixes its own color in.
        CraftingRecipe dye = recipe("minecraft:leather_helmet_dyed");
        ItemStack red = make(dye, grid(2, item("leather_helmet"), item("red_dye")));
        check("dye: red", color(red), 0xB02E26);
        check("dye: red and yellow", color(make(dye, grid(3, item("leather_helmet"), item("red_dye"),
                item("yellow_dye")))), 0xD78331);
        check("dye: a red helmet and blue", color(make(dye, grid(2, red, item("blue_dye")))), 0xAD5398);
        check("dye: the dyes' order is no matter", color(make(dye, grid(3, item("blue_dye"), item("leather_helmet"),
                item("red_dye")))), color(make(dye, grid(3, item("red_dye"), item("blue_dye"),
                item("leather_helmet")))));
        refuses("dye: two helmets", dye, grid(3, item("leather_helmet"), item("leather_helmet"), item("red_dye")));
        refuses("dye: no dye", dye, grid(1, item("leather_helmet")));
        refuses("dye: something else", dye, grid(3, item("leather_helmet"), item("red_dye"), item("stick")));

        // A firework star: its shape, colors in the grid's order, trail and twinkle.
        CraftingRecipe star = recipe("minecraft:firework_star");
        ItemStack burst = make(star, grid(5, item("gunpowder"), item("red_dye"), item("blue_dye"), item("diamond"),
                item("feather")));
        check("star: burst, red and blue, trail", burst.get(DataComponents.FIREWORK_EXPLOSION),
                new FireworkExplosion(FireworkExplosion.Shape.BURST, ints(DyeColor.RED.getFireworkColor(),
                        DyeColor.BLUE.getFireworkColor()), ints(), true, false));
        check("star: a plain one", make(star, grid(3, item("gunpowder"), item("lime_dye"), item("glowstone_dust")))
                .get(DataComponents.FIREWORK_EXPLOSION), new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                ints(DyeColor.LIME.getFireworkColor()), ints(), false, true));
        check("star: a creeper's head", make(star, grid(3, item("gunpowder"), item("red_dye"),
                item("creeper_head"))).get(DataComponents.FIREWORK_EXPLOSION).shape(), FireworkExplosion.Shape.CREEPER);
        refuses("star: two gunpowder", star, grid(3, item("gunpowder"), item("gunpowder"), item("red_dye")));
        refuses("star: no dye", star, grid(2, item("gunpowder"), item("feather")));
        refuses("star: two shapes", star, grid(4, item("gunpowder"), item("red_dye"), item("feather"),
                item("fire_charge")));

        // Its fade: the dyes' colors added to the star's explosion (a plain star gets the default one).
        CraftingRecipe fade = recipe("minecraft:firework_star_fade");
        check("fade: green on the burst", make(fade, grid(2, burst, item("green_dye")))
                .get(DataComponents.FIREWORK_EXPLOSION), burst.get(DataComponents.FIREWORK_EXPLOSION)
                .withFadeColors(ints(DyeColor.GREEN.getFireworkColor())));
        check("fade: on a plain star", make(fade, grid(2, item("firework_star"), item("white_dye")))
                .get(DataComponents.FIREWORK_EXPLOSION), FireworkExplosion.DEFAULT
                .withFadeColors(ints(DyeColor.WHITE.getFireworkColor())));
        refuses("fade: two stars", fade, grid(3, burst, burst, item("green_dye")));

        // Rockets: three, flying as long as the gunpowder says, bursting as the stars do.
        CraftingRecipe rocket = recipe("minecraft:firework_rocket");
        ItemStack rockets = make(rocket, grid(4, item("paper"), item("gunpowder"), item("gunpowder"), burst));
        check("rocket: count", rockets.getCount(), 3);
        check("rocket: flight and explosions", rockets.get(DataComponents.FIREWORKS),
                new Fireworks(2, List.of(burst.get(DataComponents.FIREWORK_EXPLOSION))));
        check("rocket: without a star", make(rocket, grid(2, item("paper"), item("gunpowder")))
                .get(DataComponents.FIREWORKS), new Fireworks(1, List.of()));
        refuses("rocket: four gunpowder", rocket, grid(5, item("paper"), item("gunpowder"), item("gunpowder"),
                item("gunpowder"), item("gunpowder")));
        refuses("rocket: no paper", rocket, grid(1, item("gunpowder")));

        // A shield decorated with a banner: the banner's patterns and color.
        JsonArray patterns = JsonParser.parseString(
                "[{\"pattern\":\"minecraft:stripe_top\",\"color\":\"black\"}]").getAsJsonArray();
        ItemStack banner = item("red_banner");
        banner.set(DataComponents.BANNER_PATTERNS, patterns);
        CraftingRecipe shield = recipe("minecraft:shield_decoration");
        ItemStack decorated = make(shield, grid(2, banner, item("shield")));
        check("shield: patterns", decorated.get(DataComponents.BANNER_PATTERNS), patterns);
        check("shield: color", decorated.get(DataComponents.BASE_COLOR), DyeColor.RED);
        check("shield: an item", decorated.getItem(), item("shield").getItem());
        refuses("shield: already decorated", shield, grid(2, item("blue_banner"), decorated));
        refuses("shield: two banners", shield, grid(3, banner, item("red_banner"), item("shield")));

        // A banner copied: the patterned one onto a blank one, the patterned one staying.
        CraftingRecipe duplicate = recipe("minecraft:red_banner_duplicate");
        CraftingInput copying = grid(2, item("red_banner"), banner);
        check("banner: copy", make(duplicate, copying).get(DataComponents.BANNER_PATTERNS), patterns);
        check("banner: the patterned one stays", stays(duplicate, copying),
                List.of("", "minecraft:red_banner " + patterns));
        refuses("banner: two blank", duplicate, grid(2, item("red_banner"), item("red_banner")));
        refuses("banner: two patterned", duplicate, grid(2, banner, banner));

        // A written book copied: a generation on, as many as book and quills, the book staying; a copy of a copy
        // can't be.
        CraftingRecipe cloning = recipe("minecraft:book_cloning");
        ItemStack book = item("written_book");
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"title\":{\"raw\":\"Notes\"},\"author\":\"Kiln\",\"pages\":[\"Hi\"]}"))
                .getOrThrow());
        CraftingInput cloningGrid = grid(3, book, item("writable_book"), item("writable_book"));
        ItemStack copies = make(cloning, cloningGrid);
        check("book: copies", copies.getCount(), 2);
        check("book: a generation on", generation(copies), 1);
        check("book: the same title", copies.get(DataComponents.WRITTEN_BOOK_CONTENT).title().raw(), "Notes");
        check("book: the same pages", copies.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().get(0).raw().getString(),
                "Hi");
        check("book: the original stays", stays(cloning, cloningGrid).get(0).startsWith("minecraft:written_book"),
                true);
        check("book: a copy of the original", generation(make(cloning, grid(2, copies.copyWithCount(1),
                item("writable_book")))), 2);
        ItemStack copyOfCopy = make(cloning, grid(2, copies.copyWithCount(1), item("writable_book")));
        refuses("book: a copy of a copy", cloning, grid(2, copyOfCopy, item("writable_book")));
        refuses("book: without book and quill", cloning, grid(1, book));
        refuses("book: without content", cloning, grid(2, item("written_book"), item("writable_book")));

        // Two worn tools repaired: both durabilities and a twentieth more, only the curses kept.
        CraftingRecipe repair = recipe("minecraft:repair_item");
        ItemStack worn = item("iron_pickaxe");
        worn.setDamageValue(200);
        worn.set(DataComponents.ENCHANTMENTS, enchantments("{\"minecraft:vanishing_curse\":1,\"minecraft:efficiency\":3}"));
        ItemStack other = item("iron_pickaxe");
        other.setDamageValue(100);
        other.set(DataComponents.ENCHANTMENTS, enchantments("{\"minecraft:binding_curse\":1,\"minecraft:unbreaking\":2}"));
        ItemStack repaired = make(repair, grid(2, worn, other));
        check("repair: durability", repaired.getDamageValue(), 250 - (50 + 150 + 250 * 5 / 100));
        check("repair: curses only", enchantmentIds(repaired),
                List.of("minecraft:binding_curse 1", "minecraft:vanishing_curse 1"));
        check("repair: no curses, no enchantments", make(repair, grid(2, item("iron_pickaxe"), item("iron_pickaxe")))
                .get(DataComponents.ENCHANTMENTS) == null || make(repair, grid(2, item("iron_pickaxe"),
                item("iron_pickaxe"))).get(DataComponents.ENCHANTMENTS).isEmpty(), true);
        refuses("repair: two kinds", repair, grid(2, item("iron_pickaxe"), item("stone_pickaxe")));
        refuses("repair: three", repair, grid(3, item("iron_pickaxe"), item("iron_pickaxe"), item("iron_pickaxe")));

        // A map cloned onto one to eight empty maps, as many as there are then.
        CraftingRecipe clone = recipe("minecraft:map_cloning");
        ItemStack map = item("filled_map");
        check("map clone: three", make(clone, grid(4, map, item("map"), item("map"), item("map"))).getCount(), 4);
        List<ItemStack> nine = new ArrayList<>(List.of(map));
        for (int i = 0; i < 8; i++) {
            nine.add(item("map"));
        }
        check("map clone: eight", make(clone, CraftingInput.of(3, 3, nine)).getCount(), 9);
        refuses("map clone: no empty map", clone, grid(1, map));

        // A map zoomed out: the copy marked to be scaled.
        CraftingRecipe extending = recipe("minecraft:map_extending");
        List<ItemStack> ring = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ring.add(i == 4 ? map : item("paper"));
        }
        check("map extend: scaled", make(extending, CraftingInput.of(3, 3, ring))
                .get(DataComponents.MAP_POST_PROCESSING), MapPostProcessing.SCALE);

        // A decorated pot: its sides as the game saves them; four bricks make a plain one.
        CraftingRecipe pot = recipe("minecraft:decorated_pot");
        List<ItemStack> sides = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            sides.add(i == 3 ? item("angler_pottery_sherd") : i % 2 == 1 ? item("brick") : ItemStack.EMPTY);
        }
        check("pot: sides", make(pot, CraftingInput.of(3, 3, sides)).get(DataComponents.POT_DECORATIONS),
                JsonParser.parseString("[\"minecraft:brick\",\"minecraft:angler_pottery_sherd\",\"minecraft:brick\","
                        + "\"minecraft:brick\"]"));
        sides.set(3, item("brick"));
        check("pot: plain", make(pot, CraftingInput.of(3, 3, sides)).get(DataComponents.POT_DECORATIONS), null);

        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static CraftingRecipe recipe(String id) {
        for (RecipeHolder<?> holder : recipes.values()) {
            if (holder.id().identifier().toString().equals(id)) {
                return (CraftingRecipe) holder.value();
            }
        }
        throw new IllegalStateException("no recipe " + id);
    }

    static ItemStack item(String id) {
        return new ItemStack(Item.kiln$byId("minecraft:" + id));
    }

    /** {@code stacks} in a grid {@code width} wide, one row. */
    static CraftingInput grid(int width, ItemStack... stacks) {
        return CraftingInput.of(width, 1, List.of(stacks));
    }

    static ItemStack make(CraftingRecipe recipe, CraftingInput grid) {
        if (!recipe.matches(grid, null)) {
            throw new IllegalStateException(recipe.getClass().getSimpleName() + " doesn't match " + grid.items());
        }
        return recipe.assemble(grid);
    }

    static void refuses(String what, CraftingRecipe recipe, CraftingInput grid) {
        check(what + " (refused)", recipe.matches(grid, null), false);
    }

    static int color(ItemStack stack) {
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed == null ? -1 : dyed.rgb();
    }

    static int generation(ItemStack book) {
        return book.get(DataComponents.WRITTEN_BOOK_CONTENT).generation();
    }

    static it.unimi.dsi.fastutil.ints.IntList ints(int... values) {
        return new it.unimi.dsi.fastutil.ints.IntArrayList(values);
    }

    static ItemEnchantments enchantments(String json) {
        return ItemEnchantments.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    static List<String> enchantmentIds(ItemStack stack) {
        ItemEnchantments e = stack.get(DataComponents.ENCHANTMENTS);
        List<String> ids = new ArrayList<>();
        if (e != null) {
            for (var entry : e.entrySet()) {
                ids.add(entry.getKey().unwrapKey().map(k -> k.identifier().toString()).orElse("?") + " "
                        + entry.getIntValue());
            }
        }
        ids.sort(null);
        return ids;
    }

    /** What stays in the grid, a stack's id and its banner patterns (empty for nothing), slot by slot. */
    static List<String> stays(CraftingRecipe recipe, CraftingInput grid) {
        NonNullList<ItemStack> left = recipe.getRemainingItems(grid);
        List<String> out = new ArrayList<>();
        for (ItemStack stack : left) {
            Object patterns = stack.isEmpty() ? null : stack.get(DataComponents.BANNER_PATTERNS);
            out.add(stack.isEmpty() ? "" : stack.getItem().kiln$id() + (patterns != null ? " " + patterns : ""));
        }
        return out;
    }

    static void check(String what, Object got, Object want) {
        if (Objects.equals(got, want)) {
            passed++;
            System.out.println("ok " + what);
        } else {
            failed++;
            System.out.println("FAILED " + what + ": got " + got + ", want " + want);
        }
    }
}
