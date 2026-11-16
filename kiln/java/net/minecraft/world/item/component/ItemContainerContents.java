package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Kiln's stand-in for {@code ItemContainerContents}: what a shulker box, a chest or a crafter item holds, by slot,
 * read from the stack's {@code container} as the game saves it ({@code [{"slot": 0, "item": {...}}]}).
 */
public final class ItemContainerContents implements ContainerComponent<ItemContainerContents>, TooltipProvider {
    public static final int MAX_SIZE = 256;
    public static final ItemContainerContents EMPTY = new ItemContainerContents(List.of());

    private record Slot(int index, ItemStack item) {}

    private static final Codec<Slot> SLOT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, MAX_SIZE - 1).fieldOf("slot").forGetter(Slot::index),
            ItemStack.CODEC.fieldOf("item").forGetter(Slot::item))
            .apply(i, Slot::new));
    public static final Codec<ItemContainerContents> CODEC = SLOT_CODEC.listOf().xmap(
            ItemContainerContents::fromSlots, ItemContainerContents::slots);

    /** By slot, an empty stack where a slot holds nothing. */
    private final List<ItemStack> items;

    private ItemContainerContents(List<ItemStack> items) {
        this.items = items;
    }

    protected ItemContainerContents() {
        this(List.of());
    }

    private static ItemContainerContents fromSlots(List<Slot> slots) {
        int size = 0;
        for (Slot slot : slots) {
            size = Math.max(size, slot.index + 1);
        }
        List<ItemStack> items = new ArrayList<>(java.util.Collections.nCopies(size, ItemStack.EMPTY));
        for (Slot slot : slots) {
            items.set(slot.index, slot.item);
        }
        return new ItemContainerContents(List.copyOf(items));
    }

    private List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                slots.add(new Slot(i, items.get(i)));
            }
        }
        return slots;
    }

    public static ItemContainerContents fromItems(List<ItemStack> stacks) {
        int last = stacks.size() - 1;
        while (last >= 0 && stacks.get(last).isEmpty()) {
            last--;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            items.add(stacks.get(i).copy());
        }
        return items.isEmpty() ? EMPTY : new ItemContainerContents(List.copyOf(items));
    }

    public void copyInto(NonNullList<ItemStack> into) {
        for (int i = 0; i < into.size(); i++) {
            into.set(i, i < items.size() ? items.get(i).copy() : ItemStack.EMPTY);
        }
    }

    public ItemStack copyOne() {
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0).copy();
    }

    @Override
    public Stream<ItemStack> itemCopies() {
        return items.stream().map(ItemStack::copy);
    }

    public Stream<ItemStack> nonEmptyItemCopyStream() {
        return items.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy);
    }

    /** The stacks it holds, the empty slots left out; their own components are left out ({@link ItemStackTemplate}). */
    public Iterable<ItemStackTemplate> nonEmptyItems() {
        return items.stream().filter(s -> !s.isEmpty()).map(ItemStackTemplate::fromNonEmptyStack).toList();
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public ItemContainerContents copyWithContents(Stream<ItemStack> stacks) {
        return fromItems(stacks.toList());
    }

    @Override
    public ItemContainerContents.Mutable asMutable() {
        kiln.Missing.called("net.minecraft.world.item.component.ItemContainerContents.asMutable");
        return null;
    }

    @Override
    public void addToTooltip(net.minecraft.world.item.Item.TooltipContext context,
            java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag,
            net.minecraft.core.component.DataComponentGetter components) {
        kiln.Missing.called("net.minecraft.world.item.component.ItemContainerContents.addToTooltip");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemContainerContents other && slots().equals(other.slots());
    }

    @Override
    public int hashCode() {
        return slots().hashCode();
    }

    public static class Mutable extends GrowableMutableContainer<ItemContainerContents> {
        public ItemContainerContents toImmutable() {
            kiln.Missing.called("net.minecraft.world.item.component.ItemContainerContents.Mutable.toImmutable");
            return null;
        }

        protected Mutable() {
            super();
        }
    }
}
