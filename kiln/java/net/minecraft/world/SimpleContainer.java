package net.minecraft.world;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code SimpleContainer}: a fixed number of item slots, as a mod's menu keeps its own (a crafting
 * station's input and result, D-165). What it holds lives in Java; Horizon draws the menu from the slots.
 */
public class SimpleContainer implements Container {
    private final int size;
    private final NonNullList<ItemStack> items;

    public SimpleContainer(int size) {
        this.size = size;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public SimpleContainer(ItemStack... stacks) {
        this(stacks.length);
        for (int i = 0; i < stacks.length; i++) {
            items.set(i, stacks[i]);
        }
    }

    @Override
    public int getContainerSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < size ? items.get(slot) : ItemStack.EMPTY;
    }

    public List<ItemStack> removeAllItems() {
        List<ItemStack> taken = items.stream().filter(s -> !s.isEmpty()).toList();
        clearContent();
        return taken;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, count);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    public ItemStack removeItemType(Item item, int count) {
        ItemStack taken = new ItemStack(item, 0);
        for (int i = size - 1; i >= 0 && taken.getCount() < count; i--) {
            ItemStack stack = items.get(i);
            if (stack.is(item)) {
                ItemStack part = stack.split(count - taken.getCount());
                taken.grow(part.getCount());
            }
        }
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    /** Puts {@code stack} into the slots that hold the same, then the empty ones; returns what didn't fit. */
    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack rest = stack.copy();
        for (int i = 0; i < size && !rest.isEmpty(); i++) {
            ItemStack here = items.get(i);
            if (!here.isEmpty() && ItemStack.isSameItemSameComponents(here, rest)) {
                int move = Math.min(rest.getCount(), getMaxStackSize(here) - here.getCount());
                if (move > 0) {
                    here.grow(move);
                    rest.shrink(move);
                }
            }
        }
        for (int i = 0; i < size && !rest.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        setChanged();
        return rest.isEmpty() ? ItemStack.EMPTY : rest;
    }

    public boolean canAddItem(ItemStack stack) {
        for (ItemStack here : items) {
            if (here.isEmpty() || ItemStack.isSameItemSameComponents(here, stack)
                    && here.getCount() < getMaxStackSize(here)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < size) {
            items.set(slot, stack);
            setChanged();
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
        for (int i = 0; i < size; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    public String toString() {
        return items.stream().filter(s -> !s.isEmpty()).toList().toString();
    }
}
