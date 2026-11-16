package net.minecraft.world.item.crafting.display;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Kiln's stand-in for {@code DisplayContentsFactory}: what a {@link SlotDisplay}'s contents become (stacks, a recipe
 * viewer's ingredients), as vanilla's.
 */
public interface DisplayContentsFactory<T> {
    interface ForStacks<T> extends DisplayContentsFactory<T> {
        default T forStack(Holder<Item> item) {
            return forStack(new ItemStack(item.value()));
        }

        default T forStack(Item item) {
            return forStack(new ItemStack(item));
        }

        default T forStack(ItemStackTemplate template) {
            return forStack(template.create());
        }

        T forStack(ItemStack stack);
    }

    interface ForRemainders<T> extends DisplayContentsFactory<T> {
        T addRemainder(T entry, List<T> remainders);
    }
}
