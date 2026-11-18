package net.fabricmc.fabric.api.transfer.v1.storage.base;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;

/**
 * Kiln's stand-in for Fabric's {@code SingleVariantItemStorage}: what one item holds (a mod's cell of a fluid), read
 * from the item in the context's slot and changed by exchanging that item for the one the subclass says it becomes.
 * As Fabric's: the item it was made for must still be in the slot, a resource goes in only where the same one is or
 * none, and taking all of it leaves the blank one.
 */
public abstract class SingleVariantItemStorage<T extends TransferVariant<?>> implements SingleSlotStorage<T> {
    protected final ContainerItemContext ctx;
    private final Item item;

    public SingleVariantItemStorage(ContainerItemContext context) {
        this.ctx = context;
        this.item = context.getItemVariant().getItem();
    }

    protected abstract T getBlankResource();

    protected abstract T getResource(ItemVariant currentVariant);

    protected abstract long getAmount(ItemVariant currentVariant);

    protected abstract long getCapacity(T variant);

    protected abstract ItemVariant getUpdatedVariant(ItemVariant currentVariant, T newResource, long newAmount);

    protected boolean canInsert(T resource) {
        return true;
    }

    protected boolean canExtract(T resource) {
        return true;
    }

    private boolean update(T resource, long amount, TransactionContext transaction) {
        return ctx.exchange(getUpdatedVariant(ctx.getItemVariant(), resource, amount), 1, transaction) == 1;
    }

    @Override
    public long insert(T resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!ctx.getItemVariant().isOf(item) || !(isResourceBlank() || resource.equals(getResource()))
                || !canInsert(resource)) {
            return 0;
        }
        long now = getAmount();
        long inserted = Math.min(maxAmount, getCapacity(resource) - now);
        return inserted > 0 && update(resource, now + inserted, transaction) ? inserted : 0;
    }

    @Override
    public long extract(T resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (!ctx.getItemVariant().isOf(item) || !resource.equals(getResource()) || !canExtract(resource)) {
            return 0;
        }
        long now = getAmount();
        long extracted = Math.min(maxAmount, now);
        if (extracted <= 0) {
            return 0;
        }
        T left = extracted == now ? getBlankResource() : getResource();
        return update(left, now - extracted, transaction) ? extracted : 0;
    }

    @Override
    public boolean isResourceBlank() {
        return getResource().isBlank();
    }

    @Override
    public T getResource() {
        return getResource(ctx.getItemVariant());
    }

    @Override
    public long getAmount() {
        return getAmount(ctx.getItemVariant());
    }

    @Override
    public long getCapacity() {
        return getCapacity(getResource());
    }

    @Override
    public String toString() {
        return "SingleVariantItemStorage[" + ctx + "/" + item + "]";
    }
}
