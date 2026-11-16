package net.minecraft.world.inventory.tooltip;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * Kiln's stand-in for {@code BundleTooltip}: the picture of a bundle's contents under its tooltip (D-177). Kiln keeps
 * the stacks whole (with their components, so an enchanted one glints) besides the {@link BundleContents}.
 */
public final class BundleTooltip implements TooltipComponent {
    private final BundleContents contents;
    private final List<ItemStack> stacks;

    public BundleTooltip(BundleContents contents) {
        this(contents, contents.itemCopies().toList());
    }

    public BundleTooltip(BundleContents contents, List<ItemStack> stacks) {
        this.contents = contents;
        this.stacks = List.copyOf(stacks);
    }

    public BundleContents contents() {
        return contents;
    }

    /** Kiln's own: the stacks inside, the last put in first, as the bundle saved them. */
    public List<ItemStack> kiln$stacks() {
        return stacks;
    }
}
