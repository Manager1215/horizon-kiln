package net.minecraft.world.item.crafting;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code RecipeManager}: the server's recipes. They are the game's: Kiln asks Horizon for the one
 * a mod's code looks for ({@code kiln.Recipes}), the cooking kinds so far.
 */
public class RecipeManager implements RecipeAccess {
    public RecipeManager(HolderLookup.Provider registries) {}

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type,
            I input, Level level) {
        return kiln.Recipes.find(type, input);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type,
            I input, Level level, RecipeHolder<T> last) {
        if (last != null && last.value().matches(input, level)) {
            return Optional.of(last);
        }
        return getRecipeFor(type, input, level);
    }

    /**
     * Every recipe, as a mod's code looks through them for its own kind (a machine's) or a recipe viewer's server
     * side makes its displays from them (REI's, D-234): the game's own as Kiln reads them from the game's prepared
     * resources, the content mods' of the game's types and the mods' own types ({@code kiln.ClientRecipes}, the
     * same holders as {@code kiln.Recipes.modRecipes}). Matching a recipe of the game's types still asks the game
     * ({@code getRecipeFor}).
     */
    public Collection<RecipeHolder<?>> getRecipes() {
        return (loaded != null ? loaded : kiln.ClientRecipes.all()).values();
    }

    /**
     * Kiln's own: the recipes this manager holds once loaded ({@link #finalizeRecipeLoading}), and their recipe-book
     * displays by recipe. Null before: a server's manager answers the mods' own recipes and asks the game for the rest.
     */
    private RecipeMap loaded;
    private java.util.Map<net.minecraft.resources.ResourceKey<Recipe<?>>, java.util.List<RecipeDisplayEntry>> displays;

    /**
     * A manager built on its own and finalized (REI's local recipes on a server without REI, D-217: Horizon's is one)
     * holds the recipes Kiln reads as the world is joined ({@code kiln.ClientRecipes}: the game's, the content mods'
     * and the mods' own types), with each recipe's displays numbered as the game numbers them, in order. The game
     * builds these from the data packs it was given; Kiln's are its prepared resources and the mods' jars.
     */
    public void finalizeRecipeLoading(net.minecraft.world.flag.FeatureFlagSet flags) {
        RecipeMap all = kiln.ClientRecipes.all();
        java.util.Map<net.minecraft.resources.ResourceKey<Recipe<?>>, java.util.List<RecipeDisplayEntry>> byRecipe =
                new java.util.HashMap<>();
        int next = 0;
        java.util.Map<String, Integer> without = new java.util.TreeMap<>();
        for (RecipeHolder<?> holder : all.values()) {
            java.util.List<RecipeDisplay> shown;
            try {
                shown = holder.value().display();
            } catch (RuntimeException | LinkageError e) {
                shown = null;
            }
            if (shown == null || shown.isEmpty()) {
                without.merge(holder.value().getClass().getSimpleName(), 1, Integer::sum);
                continue;
            }
            java.util.List<RecipeDisplayEntry> entries = new java.util.ArrayList<>();
            for (RecipeDisplay display : shown) {
                if (display != null && enabled(display, flags)) {
                    entries.add(new RecipeDisplayEntry(new RecipeDisplayId(next++), display,
                            java.util.OptionalInt.empty(), null, Optional.empty()));
                }
            }
            byRecipe.put(holder.id(), java.util.List.copyOf(entries));
        }
        displays = byRecipe;
        loaded = all;
        kiln.Native.log("Kiln's recipes for a recipe book: " + next + " displays of " + all.values().size()
                + " recipes; none for " + without);
    }

    private static boolean enabled(RecipeDisplay display, net.minecraft.world.flag.FeatureFlagSet flags) {
        try {
            return flags == null || display.isEnabled(flags);
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    /** The displays of a recipe this manager holds ({@link #finalizeRecipeLoading}), as the game's recipe book gets them. */
    public void listDisplaysForRecipe(net.minecraft.resources.ResourceKey<Recipe<?>> key,
            java.util.function.Consumer<RecipeDisplayEntry> output) {
        if (displays != null) {
            displays.getOrDefault(key, java.util.List.of()).forEach(output);
        }
    }

    /** A recipe this manager holds by its key; one Kiln hasn't loaded is none. */
    public Optional<RecipeHolder<?>> byKey(net.minecraft.resources.ResourceKey<Recipe<?>> key) {
        return Optional.ofNullable((loaded != null ? loaded : kiln.ClientRecipes.all()).byKey(key));
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.world.item.crafting.RecipeMap recipes;
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.RecipePropertySet>, net.minecraft.world.item.crafting.RecipeManager.IngredientExtractor> RECIPE_PROPERTY_SETS = null;
    public <I extends net.minecraft.world.item.crafting.RecipeInput, T extends net.minecraft.world.item.crafting.Recipe<I>> java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<T>> getRecipeFor(net.minecraft.world.item.crafting.RecipeType<T> p0, I p1, net.minecraft.world.level.Level p2, net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> p3) { kiln.Missing.called("net.minecraft.world.item.crafting.RecipeManager.getRecipeFor"); return java.util.Optional.empty(); }
    public net.minecraft.world.item.crafting.RecipeManager.ServerDisplayInfo getRecipeFromDisplay(net.minecraft.world.item.crafting.display.RecipeDisplayId p0) { kiln.Missing.called("net.minecraft.world.item.crafting.RecipeManager.getRecipeFromDisplay"); return null; }
    public static <I extends net.minecraft.world.item.crafting.RecipeInput, T extends net.minecraft.world.item.crafting.Recipe<I>> net.minecraft.world.item.crafting.RecipeManager.CachedCheck<I, T> createCheck(net.minecraft.world.item.crafting.RecipeType<T> p0) { kiln.Missing.called("net.minecraft.world.item.crafting.RecipeManager.createCheck"); return kiln.Constants.nothing(net.minecraft.world.item.crafting.RecipeManager.CachedCheck.class); }
    public static record ServerDisplayInfo(net.minecraft.world.item.crafting.display.RecipeDisplayEntry display, net.minecraft.world.item.crafting.RecipeHolder<?> parent) {
    }
    public static interface IngredientExtractor {
        java.util.Optional<net.minecraft.world.item.crafting.Ingredient> apply(net.minecraft.world.item.crafting.Recipe<?> p0);
    }
    public static interface CachedCheck<I extends net.minecraft.world.item.crafting.RecipeInput, T extends net.minecraft.world.item.crafting.Recipe<I>> {
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<T>> getRecipeFor(I p0, net.minecraft.server.level.ServerLevel p1);
    }
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
