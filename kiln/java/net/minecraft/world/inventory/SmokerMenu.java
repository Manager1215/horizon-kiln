package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Kiln's stand-in for {@code SmokerMenu}: the game's smoker menu with its slots ({@link AbstractFurnaceMenu}, D-167). The
 * game opens and runs the menu itself.
 */
public class SmokerMenu extends AbstractFurnaceMenu {
    public SmokerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(3));
    }

    /** Kiln's own: over {@code container}'s three slots (a server call's, the game's stacks in it). */
    public SmokerMenu(int containerId, Inventory inventory, Container container) {
        super(MenuType.SMOKER, containerId, inventory, container);
    }

    protected SmokerMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }
}
