package net.fabricmc.fabric.api.transfer.v1.storage;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Kiln's stand-in for Fabric's {@code StorageView}: what one part of a storage holds. */
public interface StorageView<T> {
    long extract(T resource, long maxAmount, TransactionContext transaction);

    boolean isResourceBlank();

    T getResource();

    long getAmount();

    long getCapacity();

    default StorageView<T> getUnderlyingView() {
        return this;
    }
}
