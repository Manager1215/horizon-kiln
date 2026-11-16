package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code GrindstoneScreen}: the game's grindstone's screen, which the game draws (D-156). */
public class GrindstoneScreen extends AbstractContainerScreen<net.minecraft.world.inventory.GrindstoneMenu> {
    public GrindstoneScreen(net.minecraft.world.inventory.GrindstoneMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
