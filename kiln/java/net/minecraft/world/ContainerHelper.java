package net.minecraft.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Kiln's stand-in for {@code ContainerHelper}: a container's slots saved and loaded as {@code Items}. */
public class ContainerHelper {
    public static final String TAG_ITEMS = "Items";

    public static ItemStack removeItem(List<ItemStack> stacks, int slot, int count) {
        return slot >= 0 && slot < stacks.size() && !stacks.get(slot).isEmpty() && count > 0
                ? stacks.get(slot).split(count) : ItemStack.EMPTY;
    }

    public static ItemStack takeItem(List<ItemStack> stacks, int slot) {
        return slot >= 0 && slot < stacks.size() ? stacks.set(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> stacks) {
        saveAllItems(output, stacks, true);
    }

    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> stacks, boolean alwaysPutTag) {
        ValueOutput.TypedOutputList<ItemStackWithSlot> list = output.list(TAG_ITEMS, ItemStackWithSlot.CODEC);
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty()) {
                list.add(new ItemStackWithSlot(slot, stack));
            }
        }
        if (list.isEmpty() && !alwaysPutTag) {
            output.discard(TAG_ITEMS);
        }
    }

    public static void loadAllItems(ValueInput input, NonNullList<ItemStack> stacks) {
        for (ItemStackWithSlot saved : input.listOrEmpty(TAG_ITEMS, ItemStackWithSlot.CODEC)) {
            if (saved.isValidInContainer(stacks.size())) {
                stacks.set(saved.slot(), saved.stack());
            }
        }
    }

    public static int clearOrCountMatchingItems(Container container, Predicate<ItemStack> matches, int max,
            boolean dryRun) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            int taken = clearOrCountMatchingItems(stack, matches, max - count, dryRun);
            if (taken > 0 && !dryRun && stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            count += taken;
        }
        return count;
    }

    public static int clearOrCountMatchingItems(ItemStack stack, Predicate<ItemStack> matches, int max,
            boolean dryRun) {
        if (stack.isEmpty() || !matches.test(stack)) {
            return 0;
        }
        if (dryRun) {
            return stack.getCount();
        }
        int taken = max < 0 ? stack.getCount() : Math.min(max, stack.getCount());
        stack.shrink(taken);
        return taken;
    }
}
