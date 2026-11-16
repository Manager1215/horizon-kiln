package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code SmokerScreen}: the game's smoker's screen, which the game draws (D-156). */
public class SmokerScreen extends AbstractFurnaceScreen<net.minecraft.world.inventory.SmokerMenu> {
    public SmokerScreen(net.minecraft.world.inventory.SmokerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
