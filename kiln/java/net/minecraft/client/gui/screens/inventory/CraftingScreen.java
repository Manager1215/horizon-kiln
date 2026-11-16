package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kiln's stand-in for {@code CraftingScreen}: the game's crafting table's screen, which the game draws (D-156). */
public class CraftingScreen extends AbstractRecipeBookScreen<net.minecraft.world.inventory.CraftingMenu> {
    public CraftingScreen(net.minecraft.world.inventory.CraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
