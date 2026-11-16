package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code CrafterScreen}: the game's crafter's screen, which the game draws (D-156). */
public class CrafterScreen extends AbstractContainerScreen<net.minecraft.world.inventory.CrafterMenu> {
    public CrafterScreen(net.minecraft.world.inventory.CrafterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
