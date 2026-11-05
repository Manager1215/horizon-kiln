package net.fabricmc.fabric.api.transfer.v1.storage;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Kiln's stand-in for Fabric's {@code StorageUtil}: moving resources between storages, and asking what they hold. */
public final class StorageUtil {
    private StorageUtil() {}

    /**
     * Moves up to {@code maxAmount} of what {@code from} holds and {@code filter} takes into {@code to}, view by view:
     * each view's resource goes as far as {@code to} takes it and the view gives it, or not at all. Returns how much
     * moved; the moves are part of {@code transaction} (kept at once when it is null).
     */
    public static <T> long move(Storage<T> from, Storage<T> to, Predicate<T> filter, long maxAmount,
            TransactionContext transaction) {
        if (from == null || to == null) {
            return 0;
        }
        StoragePreconditions.notNegative(maxAmount);
        long moved = 0;
        try (Transaction iteration = Transaction.openNested(transaction)) {
            for (StorageView<T> view : from.nonEmptyViews()) {
                T resource = view.getResource();
                if (!filter.test(resource)) {
                    continue;
                }
                long extractable;
                try (Transaction test = iteration.openNested()) {
                    extractable = view.extract(resource, maxAmount - moved, test);
                    test.abort();
                }
                try (Transaction transfer = iteration.openNested()) {
                    long accepted = to.insert(resource, extractable, transfer);
                    if (view.extract(resource, accepted, transfer) == accepted) {
                        moved += accepted;
                        transfer.commit();
                    }
                }
                if (moved == maxAmount) {
                    break;
                }
            }
            iteration.commit();
        }
        return moved;
    }

    /** How much of {@code resource} {@code storage} would take, changing nothing. */
    public static <T> long simulateInsert(Storage<T> storage, T resource, long maxAmount,
            TransactionContext transaction) {
        try (Transaction simulation = Transaction.openNested(transaction)) {
            return storage.insert(resource, maxAmount, simulation);
        }
    }

    /** How much of {@code resource} {@code storage} would give, changing nothing. */
    public static <T> long simulateExtract(Storage<T> storage, T resource, long maxAmount,
            TransactionContext transaction) {
        try (Transaction simulation = Transaction.openNested(transaction)) {
            return storage.extract(resource, maxAmount, simulation);
        }
    }

    /** How much of {@code resource} {@code view} would give, changing nothing. */
    public static <T> long simulateExtract(StorageView<T> view, T resource, long maxAmount,
            TransactionContext transaction) {
        try (Transaction simulation = Transaction.openNested(transaction)) {
            return view.extract(resource, maxAmount, simulation);
        }
    }

    /** The first resource {@code storage} holds, or null. */
    public static <T> T findStoredResource(Storage<T> storage) {
        return findStoredResource(storage, resource -> true);
    }

    /** The first resource {@code storage} holds that {@code filter} takes, or null. */
    public static <T> T findStoredResource(Storage<T> storage, Predicate<T> filter) {
        if (storage == null) {
            return null;
        }
        for (StorageView<T> view : storage.nonEmptyViews()) {
            if (filter.test(view.getResource())) {
                return view.getResource();
            }
        }
        return null;
    }

    /** The first resource {@code storage} would give, or null. */
    public static <T> T findExtractableResource(Storage<T> storage, TransactionContext transaction) {
        return findExtractableResource(storage, resource -> true, transaction);
    }

    /** The first resource {@code storage} would give that {@code filter} takes, or null. */
    public static <T> T findExtractableResource(Storage<T> storage, Predicate<T> filter,
            TransactionContext transaction) {
        if (storage == null) {
            return null;
        }
        try (Transaction simulation = Transaction.openNested(transaction)) {
            for (StorageView<T> view : storage.nonEmptyViews()) {
                T resource = view.getResource();
                if (filter.test(resource) && view.extract(resource, Long.MAX_VALUE, simulation) > 0) {
                    return resource;
                }
            }
        }
        return null;
    }
}
