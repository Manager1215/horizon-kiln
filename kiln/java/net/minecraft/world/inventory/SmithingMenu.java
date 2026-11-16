package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code SmithingMenu}: the game's smithing table menu with its slots as the game numbers them (the
 * template 0, the base 1, the addition 2, the result 3, the main inventory 4 to 30, the hotbar 31 to 39), so a recipe
 * viewer can move a recipe into it (D-167). The game opens and runs the menu itself.
 */
public class SmithingMenu extends AbstractContainerMenu {
    public static final int TEMPLATE_SLOT = 0;
    public static final int BASE_SLOT = 1;
    public static final int ADDITIONAL_SLOT = 2;
    public static final int RESULT_SLOT = 3;

    public SmithingMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(4));
    }

    /** Kiln's own: over {@code container}'s four slots (a server call's, the game's stacks in it). */
    public SmithingMenu(int containerId, Inventory inventory, Container container) {
        super(MenuType.SMITHING, containerId);
        addSlot(new Slot(container, TEMPLATE_SLOT, 8, 48));
        addSlot(new Slot(container, BASE_SLOT, 26, 48));
        addSlot(new Slot(container, ADDITIONAL_SLOT, 44, 48));
        addSlot(new Slot(container, RESULT_SLOT, 98, 48) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        kiln$inventory(inventory, 8, 84);
    }

    protected SmithingMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    /** The game's {@code ItemCombinerMenu.getResultSlot}. */
    public int getResultSlot() {
        return RESULT_SLOT;
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
