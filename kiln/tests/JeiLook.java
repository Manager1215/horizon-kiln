package kiln.test;

import java.util.List;
import java.util.stream.Collectors;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * A JEI plugin of kiln/tests/jei_look.rs's: once JEI's runtime is up, it prints the order of JEI's item list, the
 * ingredients as JEI made them ({@code Kiln's JEI made: ...}) and the list as JEI shows it, sorted
 * ({@code Kiln's JEI list: ...}), each by its items' ids, after how many.
 */
public class JeiLook implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("kiln_jei_look", "look");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> made = List.copyOf(runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK));
        System.out.println("Kiln's JEI made: " + made.size() + " " + ids(made));
        List<ItemStack> list = runtime.getIngredientFilter().getFilteredItemStacks();
        System.out.println("Kiln's JEI list: " + list.size() + " " + ids(list));
    }

    private static String ids(List<ItemStack> stacks) {
        return stacks.stream()
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())
                .collect(Collectors.joining(","));
    }
}
