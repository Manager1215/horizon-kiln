package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

/** Kiln's stand-in for {@code CraftingContainer}: a crafting grid's slots, row by row, and the recipe input they make. */
public interface CraftingContainer extends Container, StackedContentsCompatible {
    int getWidth();

    int getHeight();

    List<ItemStack> getItems();

    default CraftingInput asCraftInput() {
        return asPositionedCraftInput().input();
    }

    default CraftingInput.Positioned asPositionedCraftInput() {
        return CraftingInput.ofPositioned(getWidth(), getHeight(), getItems());
    }
}
