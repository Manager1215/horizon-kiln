package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code TransientCraftingContainer}: a crafting grid that lives only as long as its menu, which
 * hears of each change (a mod's machine that crafts by shaped recipes keeps one: Tech Reborn's rolling machine).
 */
public class TransientCraftingContainer implements CraftingContainer {
    private final NonNullList<ItemStack> items;
    private final int width;
    private final int height;
    private final AbstractContainerMenu menu;

    public TransientCraftingContainer(AbstractContainerMenu menu, int width, int height) {
        this(menu, width, height, NonNullList.withSize(width * height, ItemStack.EMPTY));
    }

    private TransientCraftingContainer(AbstractContainerMenu menu, int width, int height, NonNullList<ItemStack> items) {
        this.items = items;
        this.menu = menu;
        this.width = width;
        this.height = height;
    }

    protected TransientCraftingContainer() {
        this(null, 0, 0);
    }

    private void changed() {
        if (menu != null) {
            menu.slotsChanged(this);
        }
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, count);
        if (!taken.isEmpty()) {
            changed();
        }
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        changed();
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public List<ItemStack> getItems() {
        return List.copyOf(items);
    }

    /** The recipe book's count of what the grid holds: Horizon's recipe book is the game's, so Kiln counts nothing. */
    @Override
    public void fillStackedContents(StackedItemContents contents) {}
}
