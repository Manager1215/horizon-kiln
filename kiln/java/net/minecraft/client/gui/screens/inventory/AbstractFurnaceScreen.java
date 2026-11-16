package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;

/** Kiln's stand-in for {@code AbstractFurnaceScreen}: a furnace's, a blast furnace's or a smoker's screen (D-156). */
public abstract class AbstractFurnaceScreen<T extends AbstractFurnaceMenu> extends AbstractRecipeBookScreen<T> {
    public AbstractFurnaceScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
