package net.fabricmc.fabric.api.transfer.v1.storage;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Kiln's stand-in for Fabric's {@code Storage}: something that holds resources, with insert and extract. */
public interface Storage<T> extends Iterable<StorageView<T>> {
    @SuppressWarnings("unchecked")
    static <T> Storage<T> empty() {
        return (Storage<T>) EMPTY;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> Class<Storage<T>> asClass() {
        return (Class) Storage.class;
    }

    default boolean supportsInsertion() {
        return true;
    }

    long insert(T resource, long maxAmount, TransactionContext transaction);

    default boolean supportsExtraction() {
        return true;
    }

    long extract(T resource, long maxAmount, TransactionContext transaction);

    @Override
    Iterator<StorageView<T>> iterator();

    default Iterator<StorageView<T>> nonEmptyIterator() {
        List<StorageView<T>> views = new java.util.ArrayList<>();
        for (StorageView<T> view : this) {
            if (view.getAmount() > 0 && !view.isResourceBlank()) {
                views.add(view);
            }
        }
        return views.iterator();
    }

    default Iterable<StorageView<T>> nonEmptyViews() {
        return this::nonEmptyIterator;
    }

    default long getVersion() {
        return 0;
    }

    /** Kiln's own: the empty storage {@link #empty()} hands out. */
    Storage<Object> EMPTY = new Storage<>() {
        @Override
        public boolean supportsInsertion() {
            return false;
        }

        @Override
        public long insert(Object resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }

        @Override
        public long extract(Object resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public Iterator<StorageView<Object>> iterator() {
            return Collections.emptyIterator();
        }
    };
}
