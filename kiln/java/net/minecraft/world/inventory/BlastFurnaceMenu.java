package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Kiln's stand-in for {@code BlastFurnaceMenu}: the game's blast furnace menu with its slots ({@link AbstractFurnaceMenu}, D-167). The
 * game opens and runs the menu itself.
 */
public class BlastFurnaceMenu extends AbstractFurnaceMenu {
    public BlastFurnaceMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(3));
    }

    /** Kiln's own: over {@code container}'s three slots (a server call's, the game's stacks in it). */
    public BlastFurnaceMenu(int containerId, Inventory inventory, Container container) {
        super(MenuType.BLAST_FURNACE, containerId, inventory, container);
    }

    protected BlastFurnaceMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }
}
