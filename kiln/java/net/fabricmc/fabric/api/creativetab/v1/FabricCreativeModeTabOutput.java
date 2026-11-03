package net.fabricmc.fabric.api.creativetab.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** Kiln's stand-in for Fabric's {@code FabricCreativeModeTabOutput}: the stacks a tab shows, in order. */
public class FabricCreativeModeTabOutput {
    private final List<ItemStack> stacks = new ArrayList<>();

    public void accept(ItemStack stack) {
        stacks.add(stack);
    }

    public void accept(ItemLike item) {
        accept(new ItemStack(item));
    }

    public void prepend(ItemStack stack) {
        stacks.add(0, stack);
    }

    public void prepend(ItemLike item) {
        prepend(new ItemStack(item));
    }

    public List<ItemStack> getDisplayStacks() {
        return stacks;
    }
}
