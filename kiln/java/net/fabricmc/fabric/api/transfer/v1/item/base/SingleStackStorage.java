package net.fabricmc.fabric.api.transfer.v1.item.base;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

/**
 * Kiln's stand-in for Fabric's {@code SingleStackStorage}: a slot of one {@code ItemStack}, kept by its subclass
 * ({@code getStack}, {@code setStack}). A change in a transaction puts a copy in the slot first and keeps the stack
 * that was there, which comes back if the transaction is aborted.
 */
public abstract class SingleStackStorage extends SnapshotParticipant<ItemStack> implements SingleSlotStorage<ItemVariant> {
    protected abstract ItemStack getStack();

    protected abstract void setStack(ItemStack stack);

    protected boolean canInsert(ItemVariant variant) {
        return true;
    }

    protected boolean canExtract(ItemVariant variant) {
        return true;
    }

    protected int getCapacity(ItemVariant variant) {
        return variant.getItem().getDefaultMaxStackSize();
    }

    @Override
    public boolean isResourceBlank() {
        return getStack().isEmpty();
    }

    @Override
    public ItemVariant getResource() {
        return ItemVariant.of(getStack());
    }

    @Override
    public long getAmount() {
        return getStack().getCount();
    }

    @Override
    public long getCapacity() {
        return getCapacity(getResource());
    }

    @Override
    public long insert(ItemVariant variant, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(variant, maxAmount);
        ItemStack stack = getStack();
        if ((stack.isEmpty() || variant.matches(stack)) && canInsert(variant)) {
            int inserted = (int) Math.min(maxAmount, getCapacity(variant) - stack.getCount());
            if (inserted > 0) {
                updateSnapshots(transaction);
                stack = getStack();
                if (stack.isEmpty()) {
                    stack = variant.toStack(inserted);
                } else {
                    stack.grow(inserted);
                }
                setStack(stack);
                return inserted;
            }
        }
        return 0;
    }

    @Override
    public long extract(ItemVariant variant, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(variant, maxAmount);
        ItemStack stack = getStack();
        if (variant.matches(stack) && canExtract(variant)) {
            int extracted = (int) Math.min(stack.getCount(), maxAmount);
            if (extracted > 0) {
                updateSnapshots(transaction);
                stack = getStack();
                stack.shrink(extracted);
                setStack(stack);
                return extracted;
            }
        }
        return 0;
    }

    @Override
    protected ItemStack createSnapshot() {
        ItemStack original = getStack();
        setStack(original.copy());
        return original;
    }

    @Override
    protected void readSnapshot(ItemStack snapshot) {
        setStack(snapshot);
    }
}
