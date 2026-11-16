package net.minecraft.world.item.crafting;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Kiln's stand-in for {@code TransmuteResult}: what a recipe that keeps its input makes, another item or (without
 * one, {@code {}}) the input's own, with a count and components.
 */
public record TransmuteResult(Optional<Holder<Item>> item, int count, DataComponentPatch components) {
    public static final TransmuteResult KEEP_INPUT_ITEM = new TransmuteResult(Optional.empty(), 1, DataComponentPatch.EMPTY);

    public TransmuteResult(Holder<Item> item) {
        this(Optional.of(item), 1, DataComponentPatch.EMPTY);
    }

    public TransmuteResult(Item item) {
        this(Holder.direct(item));
    }

    public static TransmuteResult fromTemplate(ItemStackTemplate template) {
        return new TransmuteResult(Optional.of(template.item()), template.count(), template.components());
    }

    public ItemStackTemplate resolve(Holder<Item> input) {
        return resolve(input, count);
    }

    public ItemStackTemplate resolve(Holder<Item> input, int count) {
        return new ItemStackTemplate(item.orElse(input), count, components);
    }
}
