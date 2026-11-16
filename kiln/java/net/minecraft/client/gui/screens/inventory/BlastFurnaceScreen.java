package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code BlastFurnaceScreen}: the game's blast furnace's screen, which the game draws (D-156). */
public class BlastFurnaceScreen extends AbstractFurnaceScreen<net.minecraft.world.inventory.BlastFurnaceMenu> {
    public BlastFurnaceScreen(net.minecraft.world.inventory.BlastFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
