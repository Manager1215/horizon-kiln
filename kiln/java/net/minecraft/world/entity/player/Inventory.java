package net.minecraft.world.entity.player;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code Inventory}: a player's 36 slots (the hotbar first) and their equipment's 5 (the feet,
 * legs, chest, head and the off hand), as a menu lays them out. The window's player has the 36 as the game said with
 * the tick; a server call's own player has them from the game the first time a mod's code reaches them ({@code
 * inventory <uuid>}, D-209), what they hold from the call itself. Nothing can be added (a mod then drops it, as when
 * the inventory is full), and what a mod changes here stays in Java: the game's inventory is the server's.
 */
public class Inventory implements Container {
    public static final int INVENTORY_SIZE = 36;
    public static final int SELECTION_SIZE = 9;
    /** Slots 36 to 42, as vanilla numbers them. */
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST,
        EquipmentSlot.HEAD, EquipmentSlot.OFFHAND, EquipmentSlot.BODY, EquipmentSlot.SADDLE};

    public final Player player;
    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    /** The crafting grid of the inventory's 2x2 or the table's 3x3, row by row: what the game holds in it (D-166). */
    private final net.minecraft.world.SimpleContainer grid = new net.minecraft.world.SimpleContainer(9);
    private boolean tracked;
    private int selected;
    /** Whether the slots are as the game has them, or there is no game to ask (a client's, another player's). */
    private boolean read;

    public Inventory(Player player) {
        this.player = player;
    }

    /** The game's: {@code player}'s inventory with its equipment; Kiln's keeps the equipment slots itself. */
    public Inventory(Player player, net.minecraft.world.entity.EntityEquipment equipment) {
        this(player);
    }

    public int getSelectedSlot() {
        kiln$read();
        return selected;
    }

    /**
     * The hotbar slot in hand: the window's player's goes back to the game (Horizon's selection, which tells the
     * server as a number key does), D-211; another's stays in Java.
     */
    public void setSelectedSlot(int slot) {
        kiln$read();
        if (slot >= 0 && slot < SELECTION_SIZE) {
            if (slot != selected && player != null && player == net.minecraft.client.Minecraft.getInstance().player) {
                kiln.Back.act("selected", Integer.toString(slot));
            }
            selected = slot;
        }
    }

    /** What the player holds: the main hand. */
    public ItemStack getSelectedItem() {
        return player.getMainHandItem();
    }

    public NonNullList<ItemStack> getNonEquipmentItems() {
        kiln$read();
        return items;
    }

    /** Each of the 36 slots that holds something, with its number, as the game saves a player's {@code Inventory}. */
    public void save(net.minecraft.world.level.storage.ValueOutput.TypedOutputList<net.minecraft.world.ItemStackWithSlot> output) {
        kiln$read();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                output.add(new net.minecraft.world.ItemStackWithSlot(slot, stack));
            }
        }
    }

    /** The 36 slots from what {@link #save} wrote, the others empty, as the game reads a player's {@code Inventory}. */
    public void load(net.minecraft.world.level.storage.ValueInput.TypedInputList<net.minecraft.world.ItemStackWithSlot> input) {
        kiln$read();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        for (net.minecraft.world.ItemStackWithSlot saved : input) {
            if (saved.isValidInContainer(INVENTORY_SIZE)) {
                items.set(saved.slot(), saved.stack());
            }
        }
    }

    /**
     * Kiln's own: the 36 slots, the armour and the slot in hand as the game has them, asked once, the first time a
     * server call's own player's are reached (D-209). What the player holds stays the call's stack (the same object
     * as the hand's), so a change to it is the hand's.
     */
    public void kiln$read() {
        if (read) {
            return;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer server && !server.kiln$described()) {
            // A player still being made (their menu's slots): who they are isn't known yet.
            return;
        }
        read = true;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server) || !server.kiln$acting()) {
            return;
        }
        String[] answer = kiln.Server.ask("inventory", player.getUUID().toString()).split(" ", 2);
        if (answer.length < 2) {
            return;
        }
        int now;
        try {
            now = Integer.parseInt(answer[0]);
        } catch (NumberFormatException e) {
            return;
        }
        NonNullList<ItemStack> saved = NonNullList.withSize(INVENTORY_SIZE + 5, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(kiln.TagValues.input(kiln.Snbt.compound(answer[1])), saved);
        ItemStack held = player.getMainHandItem();
        selected = now >= 0 && now < SELECTION_SIZE ? now : 0;
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            items.set(slot, slot == selected ? held : saved.get(slot));
        }
        // The armour, feet to head; the off hand is the call's own.
        for (int worn = 0; worn < 4; worn++) {
            player.setItemSlot(EQUIPMENT[worn], saved.get(INVENTORY_SIZE + worn));
        }
    }

    /**
     * Puts {@code stack} in the slots that hold the same, then the empty ones, when a call keeps track of the 36 slots
     * ({@link #kiln$tracked}: a recipe viewer's transfer, D-166); else nothing can be added (a mod then drops it).
     */
    public boolean add(ItemStack stack) {
        if (!tracked || stack.isEmpty()) {
            return false;
        }
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int slot = 0; slot < INVENTORY_SIZE && !stack.isEmpty(); slot++) {
                ItemStack here = items.get(slot);
                if (pass == 0 && !here.isEmpty() && ItemStack.isSameItemSameComponents(here, stack)) {
                    int moved = Math.min(stack.getCount(), here.getMaxStackSize() - here.getCount());
                    if (moved > 0) {
                        here.grow(moved);
                        stack.shrink(moved);
                    }
                } else if (pass == 1 && here.isEmpty()) {
                    items.set(slot, stack.copy());
                    stack.setCount(0);
                }
            }
        }
        return stack.isEmpty();
    }

    /** Kiln's own: the crafting grid's slots (nine; the 2x2 uses the first four). */
    public net.minecraft.world.SimpleContainer kiln$grid() {
        return grid;
    }

    /** Kiln's own: whether a call keeps track of the 36 slots and the grid, to tell the game what it changed. */
    public void kiln$tracked(boolean tracked) {
        this.tracked = tracked;
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE + EQUIPMENT.length;
    }

    @Override
    public boolean isEmpty() {
        kiln$read();
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return getSelectedItem().isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        kiln$read();
        if (slot >= 0 && slot < INVENTORY_SIZE) {
            return items.get(slot);
        }
        int worn = slot - INVENTORY_SIZE;
        return worn >= 0 && worn < EQUIPMENT.length ? player.getItemBySlot(EQUIPMENT[worn]) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack stack = getItem(slot);
        return stack.isEmpty() || count <= 0 ? ItemStack.EMPTY : stack.split(count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        kiln$read();
        if (slot >= 0 && slot < INVENTORY_SIZE) {
            items.set(slot, stack);
        } else if (slot - INVENTORY_SIZE >= 0 && slot - INVENTORY_SIZE < EQUIPMENT.length) {
            player.setItemSlot(EQUIPMENT[slot - INVENTORY_SIZE], stack);
        }
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        kiln$read();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
    }

    /** Whether a slot holds a stack of {@code stack}'s item and components. */
    public boolean contains(ItemStack stack) {
        return findSlotMatchingItem(stack) >= 0;
    }

    /** The first of the 36 slots with a stack of {@code stack}'s item and components; -1 for none. */
    public int findSlotMatchingItem(ItemStack stack) {
        kiln$read();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack here = items.get(slot);
            if (!here.isEmpty() && ItemStack.isSameItemSameComponents(stack, here)) {
                return slot;
            }
        }
        return -1;
    }

    /** Kiln's own: a server call's player holds {@code stack} in the selected slot (the other slots aren't known). */
    public void kiln$hold(ItemStack stack) {
        items.set(selected, stack);
    }

    /** Kiln's own: the 36 slots and the slot in hand, as the window's game said. */
    public void kiln$set(NonNullList<ItemStack> now, int selected) {
        read = true;
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            items.set(slot, now.get(slot));
        }
        this.selected = selected;
    }

    /**
     * {@code stack} back into the slots, as the game's: those that hold the same, then the empty ones ({@link #add});
     * what doesn't fit is dropped at the player's feet, whole, with its components. A server call that doesn't keep
     * track of the 36 slots drops all of it (D-166, D-207).
     */
    public void placeItemBackInInventory(ItemStack stack, net.minecraft.util.Prediction prediction) {
        if (!stack.isEmpty() && !add(stack)) {
            player.drop(stack, false);
        }
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.minecraft.world.entity.EquipmentSlot> EQUIPMENT_SLOT_MAPPING = null;
    public net.minecraft.world.entity.EntityEquipment equipment;
    public net.minecraft.world.item.ItemStack setSelectedItem(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.setSelectedItem"); return null; }
    public static int getSelectionSize() { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.getSelectionSize"); return 0; }
    public int getFreeSlot() { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.getFreeSlot"); return 0; }
    public void addAndPickItem(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.addAndPickItem"); }
    public void pickSlot(int p0) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.pickSlot"); }
    public static boolean isHotbarSlot(int p0) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.isHotbarSlot"); return false; }
    public static boolean isUsableForCrafting(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.isUsableForCrafting"); return false; }
    public int getSuitableHotbarSlot() { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.getSuitableHotbarSlot"); return 0; }
    public int addResource(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.addResource"); return 0; }
    public int getSlotWithRemainingSpace(net.minecraft.world.item.ItemStack p0) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.getSlotWithRemainingSpace"); return 0; }
    public boolean add(int p0, net.minecraft.world.item.ItemStack p1) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.add"); return false; }
    public net.minecraft.network.chat.Component getName() { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.getName"); return null; }
    public int getTimesChanged() { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.getTimesChanged"); return 0; }
    public void replaceWith(net.minecraft.world.entity.player.Inventory p0) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.replaceWith"); }
    // End of what tools/kiln-stubs.py generated.
}
