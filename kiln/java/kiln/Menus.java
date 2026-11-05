package kiln;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A menu a mod's code opened on a click of its block ({@code Player.openMenu}): the game opens the window and moves
 * the items; Kiln tells it where the menu's slots of the block's container are on the screen, and where the player's
 * inventory starts: {@code menu <slot>:<x>:<y>,... <x> <y>} ({@code -} for no slot). Slots of other containers in the
 * menu (a machine's upgrade slots) aren't the game's to show yet.
 */
public final class Menus {
    private Menus() {}

    static void opened(AbstractContainerMenu menu, Inventory inventory, BlockEntity entity) {
        Container block = entity instanceof Container c ? c : null;
        StringBuilder slots = new StringBuilder();
        int inventoryX = Integer.MAX_VALUE;
        int inventoryY = Integer.MAX_VALUE;
        for (Slot slot : menu.slots) {
            if (slot.container == inventory) {
                // The main inventory's three rows, above the hotbar (its slots 0 to 8).
                if (slot.getContainerSlot() >= 9 && slot.getContainerSlot() < Inventory.INVENTORY_SIZE) {
                    inventoryX = Math.min(inventoryX, slot.x);
                    inventoryY = Math.min(inventoryY, slot.y);
                }
            } else if (block != null && same(slot.container, block)) {
                slots.append(slots.isEmpty() ? "" : ",").append(slot.getContainerSlot()).append(':').append(slot.x)
                        .append(':').append(slot.y);
            }
        }
        if (inventoryX == Integer.MAX_VALUE) {
            inventoryX = 8;
            inventoryY = 84;
        }
        Server.effect("menu " + (slots.isEmpty() ? "-" : slots) + " " + inventoryX + " " + inventoryY);
    }

    /**
     * Whether {@code container} is the block entity's container as the game keeps it: the block entity itself, or
     * the inventory it hands its slots to (the same stacks in each slot, as Tech Reborn's machines do).
     */
    private static boolean same(Container container, Container block) {
        if (container == block) {
            return true;
        }
        if (container.getContainerSize() != block.getContainerSize()) {
            return false;
        }
        for (int i = 0; i < block.getContainerSize(); i++) {
            if (container.getItem(i) != block.getItem(i)) {
                return false;
            }
        }
        return true;
    }
}
