package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Kiln's stand-in for {@code FurnaceMenu}: the game's furnace menu with its slots ({@link AbstractFurnaceMenu}, D-167). The
 * game opens and runs the menu itself.
 */
public class FurnaceMenu extends AbstractFurnaceMenu {
    public FurnaceMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(3));
    }

    /** Kiln's own: over {@code container}'s three slots (a server call's, the game's stacks in it). */
    public FurnaceMenu(int containerId, Inventory inventory, Container container) {
        super(MenuType.FURNACE, containerId, inventory, container);
    }

    protected FurnaceMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }
}
