package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code AbstractFurnaceMenu}: the furnaces' menus, with their slots as the game numbers them (the
 * input 0, the fuel 1, the result 2, the main inventory 3 to 29, the hotbar 30 to 38), so a recipe viewer can move a
 * recipe into one (D-167). The game opens and runs the menu itself: a client's container holds nothing Kiln sees, a
 * server call's what the game gave it for the call.
 */
public abstract class AbstractFurnaceMenu extends RecipeBookMenu {
    public static final int INGREDIENT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int RESULT_SLOT = 2;

    protected AbstractFurnaceMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    /** Kiln's own: the menu over {@code container}'s three slots and the player's inventory. */
    protected AbstractFurnaceMenu(MenuType<?> menuType, int containerId, Inventory inventory, Container container) {
        super(menuType, containerId);
        addSlot(new Slot(container, INGREDIENT_SLOT, 56, 17));
        addSlot(new Slot(container, FUEL_SLOT, 56, 53));
        addSlot(new Slot(container, RESULT_SLOT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        kiln$inventory(inventory, 8, 84);
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
