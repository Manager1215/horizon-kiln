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
}
