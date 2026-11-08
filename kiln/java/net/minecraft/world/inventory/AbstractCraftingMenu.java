package net.minecraft.world.inventory;

/**
 * Kiln's stand-in for {@code AbstractCraftingMenu}: a menu with a crafting grid, {@code width} by {@code height}, and
 * its result. The game crafts.
 */
public abstract class AbstractCraftingMenu extends RecipeBookMenu {
    public AbstractCraftingMenu(MenuType<?> menuType, int containerId, int width, int height) {
        super(menuType, containerId);
    }
}
