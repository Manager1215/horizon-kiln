package net.minecraft.client.gui.components;

/** Kiln's stand-in for {@code TabOrderedElement}. */
public interface TabOrderedElement {
    default int getTabOrderGroup() {
        return 0;
    }
}
