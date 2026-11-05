package net.fabricmc.fabric.api.transfer.v1.transaction.base;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Kiln's stand-in for Fabric's {@code SnapshotParticipant}: a storage that takes a snapshot of itself before its first
 * change in a transaction, goes back to it when the transaction is aborted, and hears the final commit.
 */
public abstract class SnapshotParticipant<T> implements TransactionContext.CloseCallback,
        TransactionContext.OuterCloseCallback {
    /** The snapshot taken in the open transaction of each depth, or null. */
    private final List<T> snapshots = new ArrayList<>();

    protected abstract T createSnapshot();

    protected abstract void readSnapshot(T snapshot);

    protected void releaseSnapshot(T snapshot) {}

    protected void onFinalCommit() {}

    public void updateSnapshots(TransactionContext transaction) {
        int depth = transaction.nestingDepth();
        while (snapshots.size() <= depth) {
            snapshots.add(null);
        }
        if (snapshots.get(depth) == null) {
            snapshots.set(depth, createSnapshot());
            transaction.addCloseCallback(this);
        }
    }

    @Override
    public void onClose(TransactionContext transaction, TransactionContext.Result result) {
        int depth = transaction.nestingDepth();
        T snapshot = snapshots.set(depth, null);
        if (result.wasAborted()) {
            readSnapshot(snapshot);
            releaseSnapshot(snapshot);
        } else if (depth > 0) {
            // Committed into the transaction around it: that one now holds the oldest snapshot.
            if (snapshots.get(depth - 1) == null) {
                snapshots.set(depth - 1, snapshot);
                transaction.getOpenTransaction(depth - 1).addCloseCallback(this);
            } else {
                releaseSnapshot(snapshot);
            }
        } else {
            releaseSnapshot(snapshot);
            transaction.addOuterCloseCallback(this);
        }
    }

    @Override
    public void afterOuterClose(TransactionContext.Result result) {
        onFinalCommit();
    }
}
