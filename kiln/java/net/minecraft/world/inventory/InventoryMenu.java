package net.minecraft.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code InventoryMenu}: the menu of a player's own inventory, which every player has ({@code
 * Player.inventoryMenu}), with no type of its own, as the game's. It is active on the server's side, not on a client's.
 * The game moves its items: Kiln's has no slots.
 */
public class InventoryMenu extends AbstractCraftingMenu {
    public final boolean active;

    public InventoryMenu(Inventory inventory, boolean active, Player owner) {
        super(null, 0, 2, 2);
        this.active = active;
    }

    /** A player's own inventory is always theirs to use. */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** The game moves the items (shift-click), not Kiln. */
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
