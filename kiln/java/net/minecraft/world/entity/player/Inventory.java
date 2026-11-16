package net.minecraft.world.entity.player;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code Inventory}: a player's 36 slots (the hotbar first) and their equipment's 5 (the feet,
 * legs, chest, head and the off hand), as a menu lays them out. The window's player has the 36 as the game said with
 * the tick; a server call knows only what the player holds and wears. Nothing can be added (a mod then drops it, as
 * when the inventory is full), and what a mod changes here stays in Java: the game's inventory is the server's.
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

    public Inventory(Player player) {
        this.player = player;
    }

    /** The game's: {@code player}'s inventory with its equipment; Kiln's keeps the equipment slots itself. */
    public Inventory(Player player, net.minecraft.world.entity.EntityEquipment equipment) {
        this(player);
    }

    public int getSelectedSlot() {
        return selected;
    }

    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < SELECTION_SIZE) {
            selected = slot;
        }
    }

    /** What the player holds: the main hand. */
    public ItemStack getSelectedItem() {
        return player.getMainHandItem();
    }

    public NonNullList<ItemStack> getNonEquipmentItems() {
        return items;
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
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return getSelectedItem().isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
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
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            items.set(slot, now.get(slot));
        }
        this.selected = selected;
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public void placeItemBackInInventory(net.minecraft.world.item.ItemStack p0, net.minecraft.util.Prediction p1) { kiln.Missing.called("net.minecraft.world.entity.player.Inventory.placeItemBackInInventory"); }
    // End of what tools/kiln-stubs.py generated.
}
