package net.minecraft.world;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuConstructor;

/** Kiln's stand-in for {@code MenuProvider}: what a player opens ({@code Player.openMenu}), and its title. */
public interface MenuProvider extends MenuConstructor {
    Component getDisplayName();
}
