package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code BrewingStandMenu}: the game's brewing stand menu with its slots as the game numbers them
 * (the three bottles 0 to 2, the ingredient 3, the blaze powder 4, the main inventory 5 to 31, the hotbar 32 to 40),
 * so a recipe viewer can move a recipe into it (D-167). The game opens and runs the menu itself.
 */
public class BrewingStandMenu extends AbstractContainerMenu {
    public BrewingStandMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(5));
    }

    /** Kiln's own: over {@code container}'s five slots (a server call's, the game's stacks in it). */
    public BrewingStandMenu(int containerId, Inventory inventory, Container container) {
        super(MenuType.BREWING_STAND, containerId);
        addSlot(new Slot(container, 0, 56, 51));
        addSlot(new Slot(container, 1, 79, 58));
        addSlot(new Slot(container, 2, 102, 51));
        addSlot(new Slot(container, 3, 79, 17));
        addSlot(new Slot(container, 4, 17, 17));
        kiln$inventory(inventory, 8, 84);
    }

    protected BrewingStandMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    /** The game moves the items (shift-click), not Kiln. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
