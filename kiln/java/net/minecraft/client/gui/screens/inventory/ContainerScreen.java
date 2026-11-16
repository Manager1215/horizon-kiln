package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code ContainerScreen}: the game's chest's (and barrel's, ender chest's, shulker box's) screen, which the game draws (D-156). */
public class ContainerScreen extends AbstractContainerScreen<net.minecraft.world.inventory.ChestMenu> {
    public ContainerScreen(net.minecraft.world.inventory.ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
