package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for {@code InteractionResult}: what using an item or a block did. Anything but {@link #PASS}
 * takes the use, as in vanilla: Horizon's own use of the item doesn't run.
 */
public interface InteractionResult {
    Success SUCCESS = new Success(SwingSource.CLIENT, ItemContext.DEFAULT);
    Success SUCCESS_SERVER = new Success(SwingSource.SERVER, ItemContext.DEFAULT);
    Success CONSUME = new Success(SwingSource.NONE, ItemContext.DEFAULT);
    Fail FAIL = new Fail();
    Pass PASS = new Pass();
    TryEmptyHandInteraction TRY_WITH_EMPTY_HAND = new TryEmptyHandInteraction();

    default boolean consumesAction() {
        return false;
    }

    enum SwingSource {
        NONE,
        CLIENT,
        SERVER
    }

    record ItemContext(boolean wasItemInteraction, ItemStack heldItemTransformedTo) {
        static final ItemContext NONE = new ItemContext(false, null);
        static final ItemContext DEFAULT = new ItemContext(true, null);
    }

    record Success(SwingSource swingSource, ItemContext itemContext) implements InteractionResult {
        @Override
        public boolean consumesAction() {
            return true;
        }

        public boolean shouldSwing() {
            return swingSource != SwingSource.NONE;
        }

        public Success heldItemTransformedTo(ItemStack stack) {
            return new Success(swingSource, new ItemContext(true, stack));
        }

        public Success withoutItem() {
            return new Success(swingSource, ItemContext.NONE);
        }

        public boolean wasItemInteraction() {
            return itemContext.wasItemInteraction();
        }

        public ItemStack heldItemTransformedTo() {
            return itemContext.heldItemTransformedTo();
        }
    }

    record Fail() implements InteractionResult {}

    record Pass() implements InteractionResult {}

    record TryEmptyHandInteraction() implements InteractionResult {}
}
