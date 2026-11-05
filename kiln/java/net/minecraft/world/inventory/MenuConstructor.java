package net.minecraft.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** Kiln's stand-in for {@code MenuConstructor}: makes a player's menu. */
@FunctionalInterface
public interface MenuConstructor {
    AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player);
}
