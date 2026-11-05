package net.fabricmc.fabric.api.transfer.v1.transaction;

import kiln.Transactions;

/**
 * Kiln's stand-in for Fabric's {@code Transaction}: storage changes that are kept only if committed. Transactions
 * nest; each thread has its own (Kiln's server calls run on Java's one).
 */
public interface Transaction extends AutoCloseable, TransactionContext {
    static Transaction openOuter() {
        return Transactions.openOuter();
    }

    static boolean isOpen() {
        return getLifecycle() != Lifecycle.NONE;
    }

    static Lifecycle getLifecycle() {
        return Transactions.lifecycle();
    }

    static Transaction openNested(TransactionContext maybeParent) {
        return maybeParent == null ? openOuter() : maybeParent.openNested();
    }

    static TransactionContext getCurrentUnsafe() {
        return Transactions.current();
    }

    void abort();

    void commit();

    @Override
    void close();

    enum Lifecycle {
        NONE,
        OPEN,
        CLOSING,
        OUTER_CLOSING
    }
}
