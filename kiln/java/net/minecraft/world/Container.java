package net.minecraft.world;

import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Kiln's stand-in for {@code Container}: item slots. A mod's block entity that is one has its slots in Horizon's
 * world, where hoppers reach them (Horizon's mod containers); Kiln hands them to the mod's code as its saved
 * {@code Items}.
 */
public interface Container extends Clearable, Iterable<ItemStack> {
    float DEFAULT_DISTANCE_BUFFER = 4.0F;

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int slot);

    ItemStack removeItem(int slot, int count);

    ItemStack removeItemNoUpdate(int slot);

    void setItem(int slot, ItemStack stack);

    default int getMaxStackSize() {
        return 99;
    }

    default int getMaxStackSize(ItemStack stack) {
        return Math.min(getMaxStackSize(), stack.getMaxStackSize());
    }

    void setChanged();

    boolean stillValid(Player player);

    default boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    default boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return true;
    }

    default int countItem(Item item) {
        int count = 0;
        for (ItemStack stack : this) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static boolean stillValidBlockEntity(BlockEntity entity, Player player) {
        return stillValidBlockEntity(entity, player, DEFAULT_DISTANCE_BUFFER);
    }

    static boolean stillValidBlockEntity(BlockEntity entity, Player player, float buffer) {
        return !entity.isRemoved();
    }

    @Override
    default Iterator<ItemStack> iterator() {
        return new Iterator<>() {
            private int slot;

            @Override
            public boolean hasNext() {
                return slot < getContainerSize();
            }

            @Override
            public ItemStack next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return getItem(slot++);
            }
        };
    }
}
