package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code ChestMenu}: a chest's screen, rows of a container's slots above the player's inventory.
 * Kiln opens only the menus of mods' own blocks in the game (D-094); a chest menu a mod's command opens (an ender
 * chest, another player's inventory) has its slots in Java, and Horizon shows nothing of it yet.
 */
public class ChestMenu extends AbstractContainerMenu {
    private final Container container;
    private final int containerRows;

    public ChestMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container container, int rows) {
        super(type, containerId);
        this.container = container;
        this.containerRows = rows;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        int below = (rows - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + below));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 161 + below));
        }
    }

    public static ChestMenu threeRows(int containerId, Inventory playerInventory, Container container) {
        return new ChestMenu(null, containerId, playerInventory, container, 3);
    }

    public static ChestMenu sixRows(int containerId, Inventory playerInventory, Container container) {
        return new ChestMenu(null, containerId, playerInventory, container, 6);
    }

    public Container getContainer() {
        return container;
    }

    public int getRowCount() {
        return containerRows;
    }

    /** What the player sees is sent whole again (Horizon shows nothing of this menu yet). */
    public void broadcastFullState() {}

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
