package net.minecraft.world.inventory;

/**
 * Kiln's stand-in for {@code MenuType}: a kind of menu, as registered. Kiln opens a mod's menu on the game's side
 * from the slots the mod's own code puts in it, so a type is only a name here.
 */
public class MenuType<T extends AbstractContainerMenu> {
    protected MenuType() {}
}
