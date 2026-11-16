package net.minecraft.world.inventory;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code CraftingMenu}: the crafting table's menu as the game numbers it (D-166): the result 0, the
 * 3x3 grid 1 to 9, the main inventory 10 to 36, the hotbar 37 to 45. The grid is the player's inventory's
 * ({@link Inventory#kiln$grid}), which the game fills (a client's from the tick, a server call's from the game); the
 * game crafts and moves the items. A recipe viewer's transfer into it needs this very class (JEI looks its handler up by
 * the menu's exact class and type).
 */
public class CraftingMenu extends AbstractCraftingMenu {
    public CraftingMenu(int containerId, Inventory inventory) {
        super(MenuType.CRAFTING, containerId, 3, 3);
        addSlot(new Slot(new SimpleContainer(1), 0, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(inventory.kiln$grid(), column + row * 3, 30 + column * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
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
