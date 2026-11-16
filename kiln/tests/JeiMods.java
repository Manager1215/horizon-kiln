package kiln.test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * A JEI plugin of kiln/tests/jei_mods.rs's: once JEI's runtime is up, it goes through every category JEI holds,
 * hidden ones too, and prints one line for each:
 * {@code Kiln's JEI category: uid=<uid> recipes=<n> stations=<n> layouts=<made>/<n> drawn=<drawn>/<tried>
 * icon=<ok|none|failed> ids=<namespace>:<n>,... title=<title>}. A layout is what JEI's recipe screen shows of a
 * recipe (the category's {@code setRecipe}: its slots, their ingredients, the widgets); a drawn one is that layout
 * drawn into a canvas of Kiln's, as the screen draws it (the category's background, slots and its own drawing). The
 * ids are the namespaces of the recipes' ids ({@code getIdentifier}): a mod's recipes in the game's categories count
 * there. What failed is printed as {@code Kiln's JEI category error: <uid> <step> <exception>}, once per category and
 * step, and a last line {@code Kiln's JEI mods done} says the walk ended.
 */
public class JeiMods implements IModPlugin {
    /** Layouts drawn per category: drawing is slower than making them, and a category draws its recipes alike. */
    private static final int DRAWN = 16;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("kiln_jei_mods", "walk");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // JEI makes its runtime current only once every plugin has had it: a layout asks for it, so the walk runs
        // on the client's next turn.
        net.minecraft.client.Minecraft.getInstance().execute(() -> walkAll(runtime));
    }

    private static void walkAll(IJeiRuntime runtime) {
        list(runtime);
        IRecipeManager recipes = runtime.getRecipeManager();
        IFocusGroup none = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        List<IRecipeCategory<?>> categories;
        try {
            categories = recipes.createRecipeCategoryLookup().includeHidden().get().toList();
        } catch (Throwable e) {
            System.out.println("Kiln's JEI category error: - categories " + e);
            System.out.println("Kiln's JEI mods done");
            return;
        }
        for (IRecipeCategory<?> category : categories) {
            try {
                walk(recipes, none, category);
            } catch (Throwable e) {
                System.out.println("Kiln's JEI category error: " + uid(category) + " walk " + e);
            }
        }
        System.out.println("Kiln's JEI mods done");
    }

    private static String uid(IRecipeCategory<?> category) {
        try {
            return category.getRecipeType().getUid().toString();
        } catch (Throwable e) {
            return category.getClass().getName();
        }
    }

    private static <T> void walk(IRecipeManager recipes, IFocusGroup none, IRecipeCategory<T> category) {
        IRecipeType<T> type = category.getRecipeType();
        String uid = type.getUid().toString();
        List<T> all = recipes.createRecipeLookup(type).includeHidden().get().toList();
        long stations = 0;
        try {
            stations = recipes.createCraftingStationLookup(type).includeHidden().get().count();
        } catch (Throwable e) {
            error(uid, "stations", e);
        }
        Map<String, Integer> ids = new TreeMap<>();
        int made = 0;
        int drawn = 0;
        int tried = 0;
        // What the drawn layouts drew (D-168): fluids as their blocks' sprites, grey boxes where a picture was missing.
        int fluids = 0;
        int fallbacks = 0;
        int entities = 0;
        boolean idFailed = false;
        boolean layoutFailed = false;
        boolean drawFailed = false;
        for (T recipe : all) {
            String namespace = "?";
            try {
                Identifier id = category.getIdentifier(recipe);
                if (id != null) {
                    namespace = id.getNamespace();
                }
            } catch (Throwable e) {
                if (!idFailed) {
                    error(uid, "id", e);
                    idFailed = true;
                }
            }
            ids.merge(namespace, 1, Integer::sum);
            Optional<IRecipeLayoutDrawable<T>> layout = Optional.empty();
            try {
                layout = recipes.createRecipeLayoutDrawable(category, recipe, none);
            } catch (Throwable e) {
                if (!layoutFailed) {
                    error(uid, "layout", e);
                    layoutFailed = true;
                }
            }
            if (layout.isEmpty()) {
                continue;
            }
            made++;
            if (tried < DRAWN) {
                tried++;
                try {
                    GuiGraphicsExtractor canvas = new GuiGraphicsExtractor(427, 240);
                    layout.get().drawRecipe(canvas, 0, 0);
                    drawn++;
                    for (String op : canvas.kiln$ops()) {
                        if ((op.startsWith("b ") || op.startsWith("B ")) && op.contains(" kiln:atlas/")) {
                            fluids++;
                        } else if (op.startsWith("f ") && op.endsWith(" " + Integer.toUnsignedString(0xC08B8B8B))) {
                            fallbacks++;
                        } else if (op.startsWith("e ") && entities++ == 0) {
                            // An entity's model drawn into the page (Just Enough Breeding's mobs, D-173).
                            System.out.println("Kiln's JEI entity op: " + uid + " " + op);
                        }
                    }
                } catch (Throwable e) {
                    if (!drawFailed) {
                        error(uid, "draw", e);
                        drawFailed = true;
                    }
                }
            }
        }
        String icon;
        try {
            IDrawable drawable = category.getIcon();
            if (drawable == null) {
                icon = "none";
            } else {
                drawable.draw(new GuiGraphicsExtractor(427, 240), 0, 0);
                icon = "ok";
            }
        } catch (Throwable e) {
            error(uid, "icon", e);
            icon = "failed";
        }
        String title;
        try {
            title = category.getTitle().getString();
        } catch (Throwable e) {
            error(uid, "title", e);
            title = "?";
        }
        StringBuilder namespaces = new StringBuilder();
        ids.forEach((namespace, count) -> namespaces.append(namespaces.isEmpty() ? "" : ",").append(namespace)
                .append(':').append(count));
        System.out.println("Kiln's JEI category: uid=" + uid + " recipes=" + all.size() + " stations=" + stations
                + " layouts=" + made + "/" + all.size() + " drawn=" + drawn + "/" + tried + " icon=" + icon + " entities=" + entities + " ids="
                + (namespaces.isEmpty() ? "-" : namespaces) + " fluids=" + fluids + " fallbacks=" + fallbacks
                + " title=" + title.replace('\n', ' '));
    }

    /**
     * JEI's item list (D-168): its first items in its order (the game's creative tabs'), and how many stacks of the
     * items that differ by their components it holds (one per enchantment and level, per potion...).
     */
    private static void list(IJeiRuntime runtime) {
        try {
            List<net.minecraft.world.item.ItemStack> shown = runtime.getIngredientFilter().getFilteredItemStacks();
            StringBuilder first = new StringBuilder();
            for (int i = 0; i < Math.min(8, shown.size()); i++) {
                first.append(i == 0 ? "" : ",").append(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(shown.get(i).getItem()));
            }
            Map<String, Integer> counts = new TreeMap<>();
            for (net.minecraft.world.item.ItemStack stack : runtime.getIngredientManager().getAllItemStacks()) {
                String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
                if (id.endsWith(":enchanted_book") || id.endsWith("potion") || id.endsWith(":tipped_arrow")
                        || id.endsWith(":firework_rocket") || id.endsWith(":firework_star") || id.endsWith("_banner")
                        || id.endsWith("_spawn_egg") || id.endsWith(":goat_horn") || id.endsWith(":suspicious_stew")
                        || id.endsWith(":painting")) {
                    String key = id.endsWith("_banner") ? "banners" : id.endsWith("_spawn_egg") ? "spawn_eggs" : id;
                    counts.merge(key, 1, Integer::sum);
                }
            }
            // Its stacks by mod (the namespace of their items' ids), and the creative tabs as JEI read them.
            Map<String, Integer> byMod = new TreeMap<>();
            for (net.minecraft.world.item.ItemStack stack : runtime.getIngredientManager().getAllItemStacks()) {
                byMod.merge(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .split(":")[0], 1, Integer::sum);
            }
            Map<String, Integer> registered = new TreeMap<>();
            for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
                registered.merge(id.getNamespace(), 1, Integer::sum);
            }
            StringBuilder tabs = new StringBuilder();
            for (net.minecraft.world.item.CreativeModeTab tab : net.minecraft.core.registries.BuiltInRegistries
                    .CREATIVE_MODE_TAB) {
                tabs.append(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)).append('=')
                        .append(tab.getDisplayItems().size()).append(' ');
            }
            // A mod's item JEI didn't list: whether its stack is empty, and the uid JEI makes of it (D-168).
            for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
                if (!id.getNamespace().equals("minecraft") && !byMod.containsKey(id.getNamespace())) {
                    net.minecraft.world.item.ItemStack stack = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getValue(id).getDefaultInstance();
                    String uid;
                    try {
                        uid = String.valueOf(runtime.getJeiHelpers().getStackHelper().getUidForStack(stack,
                                mezz.jei.api.ingredients.subtypes.UidContext.Ingredient));
                    } catch (Throwable e) {
                        uid = "threw " + e;
                    }
                    System.out.println("Kiln's JEI list misses: " + id + " empty=" + stack.isEmpty() + " uid=" + uid);
                    break;
                }
            }
            System.out.println("Kiln's JEI list by mod: " + byMod + " registered=" + registered + " tabs=" + tabs);
            // What JEI's search finds, as the player types it into its search box.
            Map<String, Integer> found = new java.util.LinkedHashMap<>();
            for (String word : new String[] {"enchanted book", "potion", "water", "banner", "firework", "spawn egg"}) {
                runtime.getIngredientFilter().setFilterText(word);
                found.put(word.replace(' ', '_'), runtime.getIngredientFilter().getFilteredItemStacks().size());
            }
            runtime.getIngredientFilter().setFilterText("");
            System.out.println("Kiln's JEI list: size=" + shown.size() + " first=" + first + " variants=" + counts
                    + " searched=" + found);
        } catch (Throwable e) {
            System.out.println("Kiln's JEI list: failed " + e);
        }
    }

    private static void error(String uid, String step, Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        StackTraceElement[] at = cause.getStackTrace();
        String where = at.length > 0 ? " at " + at[0] : "";
        System.out.println("Kiln's JEI category error: " + uid + " " + step + " " + cause.toString().replace('\n', ' ')
                + where);
    }
}
