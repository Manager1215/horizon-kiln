package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code CreativeModeInventoryScreen}: the game's creative inventory, which it draws (D-156). */
public class CreativeModeInventoryScreen extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    public CreativeModeInventoryScreen(Player player, FeatureFlagSet enabledFeatures, boolean displayOperatorCreativeTab) {
        super(new ItemPickerMenu(player), player.getInventory(), Component.empty(), 195, 136);
    }

    /** The creative inventory's menu: the tab's items (Kiln's shows none), then the hotbar. */
    public static class ItemPickerMenu extends AbstractContainerMenu {
        public ItemPickerMenu(Player player) {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
