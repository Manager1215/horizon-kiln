package net.minecraft.core.component;

/** Kiln's stand-in for {@code DataComponentPatch}: changes to a stack's components. Kiln knows only the empty one yet. */
public final class DataComponentPatch {
    public static final DataComponentPatch EMPTY = new DataComponentPatch();

    private DataComponentPatch() {}

    public boolean isEmpty() {
        return true;
    }

    public int size() {
        return 0;
    }
}
