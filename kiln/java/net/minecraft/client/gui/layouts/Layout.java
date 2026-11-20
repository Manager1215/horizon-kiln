package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Kiln's stand-in for {@code Layout}: an element that places others. Arranging one arranges the layouts inside it
 * first, so a layout knows its children's sizes when it places them.
 */
public interface Layout extends LayoutElement {
    void visitChildren(Consumer<LayoutElement> visitor);

    @Override
    default void visitWidgets(Consumer<AbstractWidget> visitor) {
        visitChildren(child -> child.visitWidgets(visitor));
    }

    default void arrangeElements() {
        visitChildren(child -> {
            if (child instanceof Layout layout) {
                layout.arrangeElements();
            }
        });
    }

    void removeChildren();
}
