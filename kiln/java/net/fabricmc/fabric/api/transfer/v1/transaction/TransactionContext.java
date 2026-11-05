package net.fabricmc.fabric.api.transfer.v1.transaction;

/** Kiln's stand-in for Fabric's {@code TransactionContext}: an open transaction, as a storage operation sees it. */
public interface TransactionContext {
    Transaction openNested();

    int nestingDepth();

    Transaction getOpenTransaction(int nestingDepth);

    void addCloseCallback(CloseCallback callback);

    void addOuterCloseCallback(OuterCloseCallback callback);

    @FunctionalInterface
    interface CloseCallback {
        void onClose(TransactionContext transaction, Result result);
    }

    @FunctionalInterface
    interface OuterCloseCallback {
        void afterOuterClose(Result result);
    }

    enum Result {
        ABORTED,
        COMMITTED;

        public boolean wasAborted() {
            return this == ABORTED;
        }

        public boolean wasCommitted() {
            return this == COMMITTED;
        }
    }
}
