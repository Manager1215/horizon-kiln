package net.minecraft.world.item;

import net.minecraft.world.level.ItemLike;

/** Kiln's stand-in for {@code ItemStack}: an item and a count. Kiln keeps no components (damage, name, ...). */
public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack((Item) null, 0);
    private final Item item;
    private int count;

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(ItemLike item, int count) {
        this.item = item == null ? null : item.asItem();
        this.count = count;
    }

    public boolean isEmpty() {
        return this == EMPTY || item == null || item == Items.AIR || count <= 0;
    }

    public Item getItem() {
        return isEmpty() ? Items.AIR : item;
    }

    public boolean is(Item item) {
        return getItem() == item;
    }

    public int getCount() {
        return isEmpty() ? 0 : count;
    }

    public void setCount(int count) {
        if (this != EMPTY) {
            this.count = count;
        }
    }

    public void grow(int n) {
        setCount(count + n);
    }

    public void shrink(int n) {
        grow(-n);
    }

    public int getMaxStackSize() {
        return getItem().getDefaultMaxStackSize();
    }

    public ItemStack copy() {
        return isEmpty() ? EMPTY : new ItemStack(item, count);
    }

    public ItemStack split(int n) {
        int taken = Math.min(n, getCount());
        ItemStack part = isEmpty() ? EMPTY : new ItemStack(item, taken);
        shrink(taken);
        return part;
    }

    @Override
    public String toString() {
        return getCount() + " " + getItem();
    }
}
