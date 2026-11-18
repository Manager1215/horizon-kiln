package net.minecraft.world;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;

/** Kiln's stand-in for {@code SimpleMenuProvider}: a menu {@code menuConstructor} makes, titled {@code title}. */
public final class SimpleMenuProvider implements MenuProvider {
    private final Component title;
    private final MenuConstructor menuConstructor;

    public SimpleMenuProvider(MenuConstructor menuConstructor, Component title) {
        this.menuConstructor = menuConstructor;
        this.title = title;
    }

    @Override
    public Component getDisplayName() {
        return title;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return menuConstructor.createMenu(containerId, inventory, player);
    }
}
