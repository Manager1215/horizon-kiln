import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.item.crafting.FireworkStarFadeRecipe;
import net.minecraft.world.item.crafting.FireworkStarRecipe;
import net.minecraft.world.item.crafting.ImbueRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;

/**
 * The client's recipes as Kiln reads them from the game's resources ({@code kiln.ClientRecipes}), counted the way a
 * recipe viewer (JEI) sorts them, for kiln/tests/jei_recipes.rs: one JSON line on stdout. It runs as a source file
 * with kiln-runtime.jar on the class path and {@code -Dkiln.resources}. {@code Host.main} isn't running, so Kiln's log
 * lines go to stderr.
 *
 * <ul>
 *   <li>{@code types}: the recipes by their class ({@code ShapedRecipe}...);
 *   <li>{@code by_type}: by their {@code RecipeType} ({@code crafting}, {@code smithing}...);
 *   <li>{@code crafting_shown}: the crafting ones JEI's crafting category shows itself (not special, with a shaped or
 *       shapeless display); {@code crafting_extended}: the special ones it shows by their class (the fireworks); {@code
 *       crafting_replaced}: the ones it makes recipes of its own from (a shield decorated, tipped arrows);
 *       {@code crafting_hidden}: the rest, which it doesn't show, as in the game.
 * </ul>
 */
public class JeiRecipes {
    public static void main(String[] args) throws Exception {
        var out = Class.forName("kiln.Host").getDeclaredField("out");
        out.setAccessible(true);
        out.set(null, new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        RecipeMap map = (RecipeMap) Class.forName("kiln.ClientRecipes").getMethod("all").invoke(null);
        Map<String, Integer> types = new TreeMap<>();
        Map<String, Integer> byType = new TreeMap<>();
        int shown = 0;
        int extended = 0;
        int replaced = 0;
        int hidden = 0;
        for (RecipeHolder<?> holder : map.values()) {
            Recipe<?> recipe = holder.value();
            types.merge(recipe.getClass().getSimpleName(), 1, Integer::sum);
            byType.merge(String.valueOf(recipe.getType()), 1, Integer::sum);
            if (!(recipe instanceof CraftingRecipe crafting)) {
                continue;
            }
            if (crafting instanceof FireworkRocketRecipe || crafting instanceof FireworkStarRecipe
                    || crafting instanceof FireworkStarFadeRecipe) {
                extended++;
            } else if (crafting instanceof ShieldDecorationRecipe || crafting instanceof ImbueRecipe) {
                replaced++;
            } else if (!crafting.isSpecial() && first(crafting.display()) instanceof RecipeDisplay d
                    && (d instanceof ShapedCraftingRecipeDisplay || d instanceof ShapelessCraftingRecipeDisplay)) {
                shown++;
            } else {
                hidden++;
            }
        }
        StringBuilder json = new StringBuilder("{\"total\":").append(map.values().size());
        json.append(",\"types\":").append(object(types)).append(",\"by_type\":").append(object(byType));
        json.append(",\"crafting_shown\":").append(shown).append(",\"crafting_extended\":").append(extended);
        json.append(",\"crafting_replaced\":").append(replaced).append(",\"crafting_hidden\":").append(hidden);
        System.out.println(json.append('}'));
    }

    private static RecipeDisplay first(List<RecipeDisplay> displays) {
        return displays == null || displays.isEmpty() ? null : displays.getFirst();
    }

    private static String object(Map<String, Integer> counts) {
        StringBuilder s = new StringBuilder("{");
        counts.forEach((k, v) -> s.append(s.length() > 1 ? "," : "").append('"').append(k).append("\":").append(v));
        return s.append('}').toString();
    }
}
