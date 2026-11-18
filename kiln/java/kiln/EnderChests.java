package kiln;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

/**
 * A server call's player's ender chest on Kiln's server side (D-209): Horizon keeps its slots, as it keeps a chest's
 * (D-144). The first time the call reaches it ({@code getEnderChestInventory}), Kiln asks the game for them ({@code
 * ender_chest <uuid>}); when the call ends, what it changed goes back ({@code ender_chest {Before:[...],Items:[...]}}:
 * the game changes only the slots that still hold what Kiln read). The game knows only the call's own player's: another
 * player's ender chest is empty here, and what a mod puts in it goes nowhere.
 */
public final class EnderChests {
    /** The ender chest the running call reached, and its slots as the game had them; null when none. */
    private static PlayerEnderChestContainer reached;
    private static List<ItemStack> before;
    /** Whether Kiln's log said once that another player's ender chest is empty here. */
    private static boolean toldOthers;

    private EnderChests() {}

    /** Fills {@code chest}, {@code player}'s, from the game: during a server call, for the call's own player. */
    public static void fill(ServerPlayer player, PlayerEnderChestContainer chest) {
        if (!Server.inCall()) {
            return;
        }
        String answer = Server.ask("ender_chest", player.getUUID().toString()).trim();
        if (answer.isEmpty()) {
            if (!toldOthers) {
                toldOthers = true;
                Host.send("log", "the game gives only the server call's own player's ender chest; "
                        + player.getScoreboardName() + "'s is empty here");
            }
            return;
        }
        NonNullList<ItemStack> slots = NonNullList.withSize(chest.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(TagValues.input(Snbt.compound(answer)), slots);
        List<ItemStack> was = new ArrayList<>();
        for (int slot = 0; slot < slots.size(); slot++) {
            chest.setItem(slot, slots.get(slot));
            was.add(slots.get(slot).copy());
        }
        reached = chest;
        before = was;
    }

    /** The call is done: the ender chest's slots it changed go back to the game. */
    static void save() {
        PlayerEnderChestContainer chest = reached;
        List<ItemStack> was = before;
        drop();
        if (chest == null) {
            return;
        }
        boolean changed = false;
        NonNullList<ItemStack> now = NonNullList.withSize(was.size(), ItemStack.EMPTY);
        NonNullList<ItemStack> then = NonNullList.withSize(was.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < was.size(); slot++) {
            now.set(slot, chest.getItem(slot));
            then.set(slot, was.get(slot));
            changed |= !ItemStack.matches(was.get(slot), chest.getItem(slot));
        }
        if (!changed) {
            return;
        }
        Map<String, Object> thenSaved = new TreeMap<>();
        ContainerHelper.saveAllItems(TagValues.output(thenSaved), then);
        Map<String, Object> saved = new TreeMap<>();
        ContainerHelper.saveAllItems(TagValues.output(saved), now);
        saved.put("Before", thenSaved.get(ContainerHelper.TAG_ITEMS));
        Server.effect("ender_chest " + Snbt.write(saved));
    }

    /** The call failed, or ended: the game keeps the slots as they are. */
    static void drop() {
        reached = null;
        before = null;
    }
}
