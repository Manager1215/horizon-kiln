package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code SmithingScreen}: the game's smithing table's screen, which the game draws (D-156). */
public class SmithingScreen extends AbstractContainerScreen<net.minecraft.world.inventory.SmithingMenu> {
    public SmithingScreen(net.minecraft.world.inventory.SmithingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
