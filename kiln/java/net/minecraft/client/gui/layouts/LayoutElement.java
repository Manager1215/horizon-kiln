package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;

/** Kiln's stand-in for {@code LayoutElement}: something placed at (x, y), so big. */
public interface LayoutElement {
    void setX(int x);

    void setY(int y);

    int getX();

    int getY();

    int getWidth();

    int getHeight();

    default void setPosition(int x, int y) {
        setX(x);
        setY(y);
    }

    void visitWidgets(Consumer<AbstractWidget> visitor);
}
