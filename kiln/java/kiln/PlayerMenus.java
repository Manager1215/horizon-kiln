package kiln;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The menu the game has the player in, for a mod's server code that moves their items (D-166, D-167: a recipe viewer's
 * "+" moves a recipe's ingredients from the inventory into the crafting grid, a furnace, a smithing table...; its cheat
 * mode gives an item into the inventory or onto the cursor). Horizon keeps the stacks, so for a message from the player
 * Kiln asks the game ({@code player_menu}) for the menu's kind, the inventory's 36 slots, the menu's own slots and the
 * cursor's stack; the player's {@code containerMenu} is that menu with those stacks in its slots; and what the call
 * changed goes back as one {@code slots} effect, which the game applies when every slot still holds what the call read.
 */
public final class PlayerMenus {
    /** The call's view of the menu while a call runs, else null. */
    private static Open current;

    private PlayerMenus() {}

    /**
     * Kiln's own (D-176): whether the stack a mod's code drops at the player's feet in this call is what it took out of a
     * station's own slot and found no room for (JEI's {@code stowItems}). The game drops that from the slot itself
     * (Kiln's replay of the move throws it, as the game's drop key over the slot), so the call makes no item of it.
     */
    public static boolean droppedFromStation(ItemStack stack) {
        Open open = current;
        if (open == null || open.kind.equals("2") || open.kind.equals("3") || open.cells == 0 || stack.isEmpty()) {
            return false;
        }
        for (int cell = 0; cell < open.cells; cell++) {
            if (ItemStack.isSameItemSameComponents(open.before.get(Inventory.INVENTORY_SIZE + cell), stack)) {
                return true;
            }
        }
        return false;
    }

    /** A call's view of the menu: the stacks as the game had them, and the player's own menu before. */
    static final class Open {
        private final ServerPlayer player;
        private final String kind;
        private final int cells;
        private final AbstractContainerMenu menu;
        private final NonNullList<ItemStack> before;
        private final ItemStack carried;
        private final AbstractContainerMenu previous;

        private Open(ServerPlayer player, String kind, AbstractContainerMenu menu, NonNullList<ItemStack> stacks,
                ItemStack carried) {
            this.player = player;
            this.kind = kind;
            this.cells = cells(kind);
            this.menu = menu;
            this.before = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < stacks.size(); slot++) {
                before.set(slot, stacks.get(slot).copy());
            }
            this.carried = carried.copy();
            this.previous = player.containerMenu;
        }

        /** The call is over: what it did to the slots goes to the game, the player's menu is as it was. */
        void close() {
            Inventory inventory = player.getInventory();
            inventory.kiln$tracked(false);
            if (current == this) {
                current = null;
            }
            ItemStack carriedNow = menu.getCarried();
            player.containerMenu = previous;
            NonNullList<ItemStack> now = read(inventory, kind, menu, cells);
            boolean changed = !ItemStack.matches(carried, carriedNow);
            for (int slot = 0; slot < now.size(); slot++) {
                changed |= !ItemStack.matches(before.get(slot), now.get(slot));
            }
            if (!changed) {
                return;
            }
            Map<String, Object> was = new TreeMap<>();
            ContainerHelper.saveAllItems(TagValues.output(was), before);
            Map<String, Object> saved = new TreeMap<>();
            ContainerHelper.saveAllItems(TagValues.output(saved), now);
            saved.put("Before", was.get(ContainerHelper.TAG_ITEMS));
            saved.put("Kind", kind);
            if (!ItemStack.matches(carried, carriedNow)) {
                saved.put("CarriedBefore", stackTag(carried));
                saved.put("Carried", stackTag(carriedNow));
            }
            Server.effect("slots " + Snbt.write(saved));
        }
    }

    /** A stack as the game saves one (an empty compound for none). */
    private static Map<String, Object> stackTag(ItemStack stack) {
        Map<String, Object> tag = new TreeMap<>();
        if (!stack.isEmpty()) {
            NonNullList<ItemStack> one = NonNullList.withSize(1, ItemStack.EMPTY);
            one.set(0, stack);
            Map<String, Object> list = new TreeMap<>();
            ContainerHelper.saveAllItems(TagValues.output(list), one);
            Object items = list.get(ContainerHelper.TAG_ITEMS);
            if (items instanceof java.util.List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!"Slot".equals(e.getKey())) {
                        tag.put((String) e.getKey(), e.getValue());
                    }
                }
            }
        }
        return tag;
    }

    /** How many slots of its own a menu of {@code kind} has before the inventory's (as the game's {@code menu_cells}). */
    static int cells(String kind) {
        return switch (kind) {
            case "2" -> 4;
            case "3", "crafter" -> 9;
            case "furnace", "smoker", "blast_furnace", "anvil" -> 3;
            case "smithing" -> 4;
            case "brewing_stand" -> 5;
            default -> 0;
        };
    }

    /** The 36 slots, then the menu's own: a crafting grid's cells, else the menu's first slots. */
    private static NonNullList<ItemStack> read(Inventory inventory, String kind, AbstractContainerMenu menu, int cells) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(Inventory.INVENTORY_SIZE + cells, ItemStack.EMPTY);
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            stacks.set(slot, inventory.getNonEquipmentItems().get(slot));
        }
        boolean grid = kind.equals("2") || kind.equals("3");
        for (int cell = 0; cell < cells; cell++) {
            stacks.set(Inventory.INVENTORY_SIZE + cell, grid ? inventory.kiln$grid().getItem(cell) : menu.getSlot(cell).getItem());
        }
        return stacks;
    }

    /** The menu of {@code kind} over the player's inventory (its own slots empty): the game's class for it. */
    private static AbstractContainerMenu menu(ServerPlayer player, String kind) {
        Inventory inventory = player.getInventory();
        return switch (kind) {
            case "3" -> new CraftingMenu(1, inventory);
            case "furnace" -> new FurnaceMenu(1, inventory, new SimpleContainer(3));
            case "smoker" -> new SmokerMenu(1, inventory, new SimpleContainer(3));
            case "blast_furnace" -> new BlastFurnaceMenu(1, inventory, new SimpleContainer(3));
            case "brewing_stand" -> new BrewingStandMenu(1, inventory, new SimpleContainer(5));
            case "crafter" -> new CrafterMenu(1, inventory, new SimpleContainer(9));
            case "smithing" -> new SmithingMenu(1, inventory, new SimpleContainer(4));
            case "anvil" -> new AnvilMenu(1, inventory);
            // The inventory's own (2), and a menu whose slots the mods don't get (a chest's: 0), where a mod still
            // gives the player items and sets the cursor.
            default -> player.inventoryMenu;
        };
    }

    /**
     * The player's menu for the server call running, from what the game answers ({@code <kind> <selected slot> <SNBT>}:
     * {@code Items}, the 36 slots and then the menu's own, and {@code Carried}); null when the game has no menu on record
     * for the player, then {@code containerMenu} is as before.
     */
    static Open open(ServerPlayer player) {
        if (!Server.inCall()) {
            return null;
        }
        String[] answer = Server.ask("player_menu").split(" ", 3);
        if (answer.length < 3) {
            return null;
        }
        String kind = answer[0];
        int selected;
        try {
            selected = Integer.parseInt(answer[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        int cells = cells(kind);
        Map<String, Object> tag = Snbt.compound(answer[2]);
        NonNullList<ItemStack> stacks = NonNullList.withSize(Inventory.INVENTORY_SIZE + cells, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(TagValues.input(tag), stacks);
        ItemStack carried = ItemStack.EMPTY;
        if (tag.get("Carried") instanceof Map<?, ?> c && !c.isEmpty()) {
            Map<String, Object> list = new TreeMap<>();
            Map<String, Object> entry = new TreeMap<>();
            for (Map.Entry<?, ?> e : c.entrySet()) {
                entry.put((String) e.getKey(), e.getValue());
            }
            entry.put("Slot", (byte) 0);
            list.put(ContainerHelper.TAG_ITEMS, java.util.List.of(entry));
            NonNullList<ItemStack> one = NonNullList.withSize(1, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(TagValues.input(list), one);
            carried = one.get(0);
        }
        Inventory inventory = player.getInventory();
        NonNullList<ItemStack> main = NonNullList.withSize(Inventory.INVENTORY_SIZE, ItemStack.EMPTY);
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            main.set(slot, stacks.get(slot));
        }
        inventory.kiln$set(main, selected);
        inventory.kiln$grid().clearContent();
        AbstractContainerMenu menu = menu(player, kind);
        boolean grid = kind.equals("2") || kind.equals("3");
        for (int cell = 0; cell < cells; cell++) {
            ItemStack stack = stacks.get(Inventory.INVENTORY_SIZE + cell);
            if (grid) {
                inventory.kiln$grid().setItem(cell, stack);
            } else {
                menu.getSlot(cell).set(stack);
            }
        }
        menu.setCarried(carried.copy());
        Open open = new Open(player, kind, menu, stacks, carried);
        inventory.kiln$tracked(true);
        player.containerMenu = menu;
        current = open;
        return open;
    }
}
