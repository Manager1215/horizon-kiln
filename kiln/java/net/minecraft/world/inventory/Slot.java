package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code Slot}: one of a container's slots as a menu shows it, at {@code x y} on its screen. Its
 * stack is the container's; what it takes and gives, its subclass's say.
 */
public class Slot {
    public final Container container;
    private final int slot;
    /** Its place in the menu's list of slots. */
    public int index;
    public final int x;
    public final int y;

    public Slot(Container container, int slot, int x, int y) {
        this.container = container;
        this.slot = slot;
        this.x = x;
        this.y = y;
    }

    public void onQuickCraft(ItemStack before, ItemStack after) {}

    public void onTake(Player player, ItemStack stack) {
        setChanged();
    }

    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    public ItemStack getItem() {
        return container.getItem(slot);
    }

    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    public void setByPlayer(ItemStack stack) {
        set(stack);
    }

    public void set(ItemStack stack) {
        container.setItem(slot, stack);
        setChanged();
    }

    public void setChanged() {
        container.setChanged();
    }

    public int getMaxStackSize() {
        return container.getMaxStackSize();
    }

    public int getMaxStackSize(ItemStack stack) {
        return Math.min(getMaxStackSize(), stack.getMaxStackSize());
    }

    public ItemStack remove(int amount) {
        return container.removeItem(slot, amount);
    }

    public boolean mayPickup(Player player) {
        return true;
    }

    public boolean isActive() {
        return true;
    }

    public int getContainerSlot() {
        return slot;
    }

    /** Whether it only shows a stack and holds none (a creative tab's): Kiln's slots all hold theirs. */
    public boolean isFake() {
        return false;
    }

    /** Whether {@code player} may change it: take from it and put in it, as the game says. */
    public boolean allowModification(Player player) {
        return mayPickup(player) && mayPlace(getItem());
    }

    /** {@code stack} put in as far as it fits, as the game does; what doesn't fit comes back. */
    public ItemStack safeInsert(ItemStack stack) {
        return safeInsert(stack, stack.getCount());
    }

    /** Up to {@code count} of {@code stack} put in, as far as it fits; the rest comes back. */
    public ItemStack safeInsert(ItemStack stack, int count) {
        if (stack.isEmpty() || !mayPlace(stack)) {
            return stack;
        }
        ItemStack here = getItem();
        int moved = Math.min(Math.min(count, stack.getCount()), getMaxStackSize(stack) - here.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (here.isEmpty()) {
            setByPlayer(stack.split(moved));
        } else if (ItemStack.isSameItemSameComponents(here, stack)) {
            stack.shrink(moved);
            here.grow(moved);
            setByPlayer(here);
        }
        return stack;
    }

    /** Up to {@code count} taken out by {@code player} (at most {@code decrement} at once), as the game does. */
    public ItemStack safeTake(int count, int decrement, Player player) {
        if (!mayPickup(player) || !allowModification(player) && !getItem().isEmpty() && count < getItem().getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = remove(Math.min(count, decrement));
        if (taken.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (getItem().isEmpty()) {
            setByPlayer(ItemStack.EMPTY);
        }
        onTake(player, taken);
        return taken;
    }
}
