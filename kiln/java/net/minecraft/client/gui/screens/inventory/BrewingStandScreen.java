package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code BrewingStandScreen}: the game's brewing stand's screen, which the game draws (D-156). */
public class BrewingStandScreen extends AbstractContainerScreen<net.minecraft.world.inventory.BrewingStandMenu> {
    public BrewingStandScreen(net.minecraft.world.inventory.BrewingStandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
