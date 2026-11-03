package io.github.manager1215.kilntestbench.core;

/** Thrown by a probe whose precondition isn't met (no player yet): neither a pass nor a failure. */
public final class Skip extends RuntimeException {
    public Skip(String why) {
        super(why, null, false, false);
    }
}
