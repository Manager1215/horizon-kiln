package net.minecraft.client.gui.screens.recipebook;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.RecipeBookMenu;

/**
 * Kiln's stand-in for {@code RecipeBookComponent}: the recipe book beside a container screen's window. The game shows
 * its own; Kiln's is never open (the mods that keep clear of it find nothing to keep clear of), D-156.
 */
public abstract class RecipeBookComponent<T extends RecipeBookMenu> {
    public static final int IMAGE_WIDTH = 147;
    public static final int IMAGE_HEIGHT = 166;
    // Public, as JEI's access widener makes them.
    public int width;
    public int height;
    public int xOffset;
    public final List<RecipeBookTabButton> tabButtons = new ArrayList<>();
    protected final T menu;

    protected RecipeBookComponent(T menu) {
        this.menu = menu;
    }

    public boolean isVisible() {
        return false;
    }

    public int getXOrigin() {
        return (width - IMAGE_WIDTH) / 2 - xOffset;
    }

    public int getYOrigin() {
        return (height - IMAGE_HEIGHT) / 2;
    }

    public int updateScreenPosition(int width, int imageWidth) {
        return (width - imageWidth) / 2;
    }

    /** Kiln's own: the book of a screen with one, never open. */
    public static <T extends RecipeBookMenu> RecipeBookComponent<T> kiln$closed(T menu) {
        return new RecipeBookComponent<T>(menu) {};
    }
}
