package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Kiln's stand-in for {@code BundleContents}: the stacks a bundle holds, first the one put in last, read from the
 * stack's {@code bundle_contents} as the game saves it (a list of stacks). Their own components are left out
 * ({@link ItemStackTemplate}).
 */
public final class BundleContents implements ContainerComponent<BundleContents>,
        net.minecraft.world.inventory.tooltip.TooltipComponent {
    public static final BundleContents EMPTY = new BundleContents(List.of());
    public static final Codec<BundleContents> CODEC = ItemStack.CODEC.listOf().xmap(
            stacks -> new BundleContents(stacks.stream().filter(s -> !s.isEmpty())
                    .map(ItemStackTemplate::fromNonEmptyStack).toList()),
            contents -> contents.items.stream().map(ItemStackTemplate::create).toList());
    public static final int NO_SELECTED_ITEM_INDEX = -1;

    private final List<ItemStackTemplate> items;

    public BundleContents(List<ItemStackTemplate> items) {
        this.items = List.copyOf(items);
    }

    public List<ItemStackTemplate> items() {
        return items;
    }

    public int getNumberOfItemsToShow() {
        return Math.min(items.size(), 12);
    }

    @Override
    public Stream<ItemStack> itemCopies() {
        return items.stream().map(ItemStackTemplate::create);
    }

    @Override
    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Which stack the player picked with the wheel: Kiln doesn't know, so none. */
    public int getSelectedItemIndex() {
        return NO_SELECTED_ITEM_INDEX;
    }

    public ItemStackTemplate getSelectedItem() {
        return null;
    }

    @Override
    public BundleContents copyWithContents(Stream<ItemStack> stacks) {
        return new BundleContents(stacks.filter(s -> !s.isEmpty()).map(ItemStackTemplate::fromNonEmptyStack).toList());
    }

    @Override
    public ContainerComponent.Mutable<BundleContents> asMutable() {
        kiln.Missing.called("net.minecraft.world.item.component.BundleContents.asMutable");
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BundleContents other && items.equals(other.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        return "BundleContents" + items;
    }
}
