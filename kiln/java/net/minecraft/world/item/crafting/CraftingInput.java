package net.minecraft.world.item.crafting;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Kiln's stand-in for {@code CraftingInput}: a crafting grid's stacks, row by row. */
public class CraftingInput implements RecipeInput {
    public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());

    private final int width;
    private final int height;
    private final List<ItemStack> items;

    private CraftingInput(int width, int height, List<ItemStack> items) {
        this.width = width;
        this.height = height;
        this.items = items;
    }

    protected CraftingInput() {
        this(0, 0, List.of());
    }

    public static CraftingInput of(int width, int height, List<ItemStack> items) {
        return width == 0 || height == 0 ? EMPTY : new CraftingInput(width, height, List.copyOf(items));
    }

    public ItemStack getItem(int index) {
        return items.get(index);
    }

    public ItemStack getItem(int x, int y) {
        return items.get(x + y * width);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    public List<ItemStack> items() {
        return items;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public record Positioned(CraftingInput input, int left, int top) {}
}
