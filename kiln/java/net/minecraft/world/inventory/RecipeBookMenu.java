package net.minecraft.world.inventory;

/** Kiln's stand-in for {@code RecipeBookMenu}: a menu the recipe book shows recipes for. The game runs the book. */
public abstract class RecipeBookMenu extends AbstractContainerMenu {
    public RecipeBookMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }
}
