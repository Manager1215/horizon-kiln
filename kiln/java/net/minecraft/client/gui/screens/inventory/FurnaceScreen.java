package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code FurnaceScreen}: the game's furnace's screen, which the game draws (D-156). */
public class FurnaceScreen extends AbstractFurnaceScreen<net.minecraft.world.inventory.FurnaceMenu> {
    public FurnaceScreen(net.minecraft.world.inventory.FurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
