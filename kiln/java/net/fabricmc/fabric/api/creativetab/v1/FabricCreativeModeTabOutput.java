package net.fabricmc.fabric.api.creativetab.v1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Kiln's stand-in for Fabric's {@code FabricCreativeModeTabOutput}: the stacks a tab shows, in order, and those its
 * search tab shows, as a mod's listener of {@link CreativeModeTabEvents} adds, inserts and prepends them while the tab
 * builds its contents (D-171).
 */
public class FabricCreativeModeTabOutput implements CreativeModeTab.Output {
    private final CreativeModeTab.ItemDisplayParameters context;
    private final List<ItemStack> displayStacks;
    private final List<ItemStack> searchTabStacks;

    public FabricCreativeModeTabOutput(CreativeModeTab.ItemDisplayParameters context, List<ItemStack> displayStacks,
            List<ItemStack> searchTabStacks) {
        this.context = context;
        this.displayStacks = displayStacks;
        this.searchTabStacks = searchTabStacks;
    }

    /** Kiln's own: an output of no tab, for a mod that makes one itself. */
    public FabricCreativeModeTabOutput() {
        this(null, new ArrayList<>(), new ArrayList<>());
    }

    public CreativeModeTab.ItemDisplayParameters getContext() {
        return context;
    }

    public FeatureFlagSet getEnabledFeatures() {
        return context == null ? FeatureFlagSet.of() : context.enabledFeatures();
    }

    public boolean shouldShowOpRestrictedItems() {
        return context != null && context.hasPermissions();
    }

    public List<ItemStack> getDisplayStacks() {
        return displayStacks;
    }

    public List<ItemStack> getSearchTabStacks() {
        return searchTabStacks;
    }

    @Override
    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY) {
            displayStacks.add(stack);
        }
        if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY) {
            searchTabStacks.add(stack);
        }
    }

    @Override
    public void accept(ItemStack stack) {
        accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    @Override
    public void accept(ItemLike item) {
        accept(new ItemStack(item));
    }

    public void prepend(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY) {
            displayStacks.add(0, stack);
        }
        if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY) {
            searchTabStacks.add(0, stack);
        }
    }

    public void prepend(ItemStack stack) {
        prepend(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    public void prepend(ItemLike item) {
        prepend(new ItemStack(item));
    }

    public void prepend(ItemLike item, CreativeModeTab.TabVisibility visibility) {
        prepend(new ItemStack(item), visibility);
    }

    public void insertAfter(ItemLike after, ItemLike... items) {
        insertAfter(new ItemStack(after), stacks(items));
    }

    public void insertAfter(ItemLike after, ItemStack... stacks) {
        insertAfter(new ItemStack(after), stacks);
    }

    public void insertAfter(ItemStack after, ItemLike... items) {
        insertAfter(after, stacks(items));
    }

    public void insertAfter(ItemStack after, ItemStack... stacks) {
        insertAfter(after, List.of(stacks), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    public void insertAfter(ItemStack after, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility) {
        insertAfter(s -> ItemStack.isSameItemSameComponents(s, after), stacks, visibility);
    }

    public void insertAfter(Predicate<ItemStack> after, Collection<ItemStack> stacks,
            CreativeModeTab.TabVisibility visibility) {
        if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY) {
            insert(displayStacks, after, stacks, true);
        }
        if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY) {
            insert(searchTabStacks, after, stacks, true);
        }
    }

    public void insertBefore(ItemLike before, ItemLike... items) {
        insertBefore(new ItemStack(before), stacks(items));
    }

    public void insertBefore(ItemLike before, ItemStack... stacks) {
        insertBefore(new ItemStack(before), stacks);
    }

    public void insertBefore(ItemStack before, ItemLike... items) {
        insertBefore(before, stacks(items));
    }

    public void insertBefore(ItemStack before, ItemStack... stacks) {
        insertBefore(before, List.of(stacks), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    public void insertBefore(ItemStack before, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility) {
        insertBefore(s -> ItemStack.isSameItemSameComponents(s, before), stacks, visibility);
    }

    public void insertBefore(Predicate<ItemStack> before, Collection<ItemStack> stacks,
            CreativeModeTab.TabVisibility visibility) {
        if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY) {
            insert(displayStacks, before, stacks, false);
        }
        if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY) {
            insert(searchTabStacks, before, stacks, false);
        }
    }

    /** As Fabric does: after (or before) the last (first) stack that matches, else at the end. */
    private static void insert(List<ItemStack> into, Predicate<ItemStack> anchor, Collection<ItemStack> stacks,
            boolean after) {
        int at = -1;
        for (int i = 0; i < into.size(); i++) {
            if (anchor.test(into.get(i))) {
                at = i;
                if (!after) {
                    break;
                }
            }
        }
        if (at < 0) {
            into.addAll(stacks);
        } else {
            into.addAll(after ? at + 1 : at, stacks);
        }
    }

    private static ItemStack[] stacks(ItemLike[] items) {
        ItemStack[] stacks = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            stacks[i] = new ItemStack(items[i]);
        }
        return stacks;
    }
}
