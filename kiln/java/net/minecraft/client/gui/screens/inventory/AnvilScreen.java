package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code AnvilScreen}: the game's anvil's screen, which the game draws (D-156). */
public class AnvilScreen extends AbstractContainerScreen<net.minecraft.world.inventory.AnvilMenu> {
    public AnvilScreen(net.minecraft.world.inventory.AnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
