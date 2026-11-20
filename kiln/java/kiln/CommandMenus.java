package kiln;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * The game's own menus a mod's command opens without their block (D-216): invsee's {@code /ec} (the player's ender
 * chest in a {@code ChestMenu}) and {@code /craft} (a {@code CraftingMenu} anywhere). The game shows each as it shows
 * its block's (the window, its slots and its clicks are the game's); Kiln names which one (the {@code open_menu}
 * effect) and the game opens it once the command's turn is over. A menu type is added here and in the game's side
 * ({@code command_menus} in Kiln's Rust) together.
 */
public final class CommandMenus {
    private CommandMenus() {}

    /**
     * The game's menu {@code menu} is, as the game opens it: {@code ender_chest}, {@code crafting}; null for one Kiln
     * doesn't open (another player's ender chest, a chest over a mod's own container, a mod's own menu type).
     */
    static String kind(AbstractContainerMenu menu, ServerPlayer player) {
        // The ender chest's own menu: three rows over this very player's ender chest (another player's is empty here).
        if (menu instanceof ChestMenu chest && chest.getRowCount() == 3
                && chest.getContainer() == player.getEnderChestInventory()) {
            return "ender_chest";
        }
        // A crafting table's, a mod's subclass included (invsee's stays valid away from a table, as Kiln's does).
        if (menu instanceof CraftingMenu) {
            return "crafting";
        }
        return null;
    }

    /** The game's own title of a menu {@code kind}, for one opened without a name. */
    static Component title(String kind) {
        return Component.translatable(kind.equals("ender_chest") ? "container.enderchest" : "container.crafting");
    }
}
