package kiln;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Fabric's transactions, as its API describes them: on each thread a stack of open ones, the outer at depth 0. Closing
 * one runs its close callbacks, last added first; closing the outer one then runs the outer close callbacks.
 */
public final class Transactions {
    private static final ThreadLocal<Stack> STACKS = ThreadLocal.withInitial(Stack::new);

    private Transactions() {}

    private static final class Stack {
        final List<Open> open = new ArrayList<>();
        final List<TransactionContext.OuterCloseCallback> outer = new ArrayList<>();
        Transaction.Lifecycle lifecycle = Transaction.Lifecycle.NONE;
    }

    public static Transaction openOuter() {
        Stack stack = STACKS.get();
        if (stack.lifecycle != Transaction.Lifecycle.NONE) {
            throw new IllegalStateException("a transaction is already open on this thread");
        }
        return open(stack);
    }

    public static Transaction.Lifecycle lifecycle() {
        return STACKS.get().lifecycle;
    }

    public static TransactionContext current() {
        List<Open> open = STACKS.get().open;
        return open.isEmpty() ? null : open.get(open.size() - 1);
    }

    private static Transaction open(Stack stack) {
        Open t = new Open(stack, stack.open.size());
        stack.open.add(t);
        stack.lifecycle = Transaction.Lifecycle.OPEN;
        return t;
    }

    private static final class Open implements Transaction {
        private final Stack stack;
        private final int depth;
        private final List<CloseCallback> callbacks = new ArrayList<>();
        private boolean closed;

        Open(Stack stack, int depth) {
            this.stack = stack;
            this.depth = depth;
        }

        private void innermost() {
            if (closed || stack.open.get(stack.open.size() - 1) != this || stack.lifecycle != Lifecycle.OPEN) {
                throw new IllegalStateException("not the innermost open transaction");
            }
        }

        @Override
        public Transaction openNested() {
            innermost();
            return open(stack);
        }

        @Override
        public int nestingDepth() {
            return depth;
        }

        @Override
        public Transaction getOpenTransaction(int nestingDepth) {
            return stack.open.get(nestingDepth);
        }

        @Override
        public void addCloseCallback(CloseCallback callback) {
            callbacks.add(callback);
        }

        @Override
        public void addOuterCloseCallback(OuterCloseCallback callback) {
            stack.outer.add(callback);
        }

        @Override
        public void abort() {
            end(Result.ABORTED);
        }

        @Override
        public void commit() {
            end(Result.COMMITTED);
        }

        @Override
        public void close() {
            if (!closed) {
                abort();
            }
        }

        private void end(Result result) {
            innermost();
            stack.lifecycle = Lifecycle.CLOSING;
            try {
                for (int i = callbacks.size() - 1; i >= 0; i--) {
                    callbacks.get(i).onClose(this, result);
                }
            } finally {
                closed = true;
                stack.open.remove(depth);
                stack.lifecycle = depth == 0 ? Lifecycle.OUTER_CLOSING : Lifecycle.OPEN;
            }
            if (depth == 0) {
                List<OuterCloseCallback> outer = new ArrayList<>(stack.outer);
                stack.outer.clear();
                try {
                    for (OuterCloseCallback callback : outer) {
                        callback.afterOuterClose(result);
                    }
                } finally {
                    stack.lifecycle = Lifecycle.NONE;
                }
            }
        }
    }
}
