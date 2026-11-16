package kiln.test;

import java.util.Map;
import java.util.TreeMap;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/**
 * A JEI plugin of kiln/tests/jei_start.rs's: once JEI's runtime is up, it prints how many recipes JEI holds in each of
 * its categories, hidden ones too, on one line: {@code Kiln's JEI count: <total> {<category>=<count>, ...}}. The test
 * compiles it against JEI's jar and runs it as a mod of its own (an entrypoint {@code jei_mod_plugin}).
 */
public class JeiCount implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("kiln_jei_count", "count");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        IRecipeManager recipes = runtime.getRecipeManager();
        Map<String, Long> counts = new TreeMap<>();
        recipes.createRecipeCategoryLookup().includeHidden().get()
                .forEach(category -> counts.put(category.getRecipeType().getUid().toString(), count(recipes, category)));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        System.out.println("Kiln's JEI count: " + total + " " + counts);
        anvilCosts(recipes);
    }

    /**
     * The levels JEI's anvil page shows for each of its anvil recipes (D-176), worked out as the page does it (its
     * {@code AnvilHelper}, with the first stack of each side): {@code Kiln's JEI anvil costs: <recipes> <none> <zero>
     * <failed> [examples]}, where none have no cost, zero cost 0 and failed threw.
     */
    private static void anvilCosts(IRecipeManager recipes) {
        java.util.List<mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe> anvil =
                recipes.createRecipeLookup(mezz.jei.api.constants.RecipeTypes.ANVIL).includeHidden().get().toList();
        int none = 0;
        int zero = 0;
        int failed = 0;
        java.util.List<String> examples = new java.util.ArrayList<>();
        for (var recipe : anvil) {
            if (recipe.getLeftInputs().isEmpty() || recipe.getRightInputs().isEmpty()) {
                none++;
                continue;
            }
            var left = recipe.getLeftInputs().getFirst();
            var right = recipe.getRightInputs().getFirst();
            int cost;
            try {
                cost = mezz.jei.library.plugins.vanilla.anvil.AnvilHelper.findLevelsCost(left, right);
            } catch (RuntimeException e) {
                failed++;
                if (examples.size() < 12) {
                    examples.add(left + "+" + right + "=" + e);
                }
                continue;
            }
            if (cost == 0) {
                zero++;
            } else if (cost < 0) {
                failed++;
            }
            if (cost <= 0 && examples.size() < 12 || examples.size() < 4) {
                examples.add(recipe.getUid() + ":" + left + "(" + left.getDamageValue() + ")+" + right + "=" + cost);
            }
        }
        System.out.println("Kiln's JEI anvil costs: " + anvil.size() + " " + none + " " + zero + " " + failed + " "
                + examples);
    }

    private static <T> long count(IRecipeManager recipes, IRecipeCategory<T> category) {
        IRecipeType<T> type = category.getRecipeType();
        return recipes.createRecipeLookup(type).includeHidden().get().count();
    }
}
