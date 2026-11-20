package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/** Kiln's stand-in for {@code LayoutElement}: something placed at (x, y), so big. */
public interface LayoutElement {
    void setX(int x);

    void setY(int y);

    int getX();

    int getY();

    int getWidth();

    int getHeight();

    /** The box it takes on the screen. */
    default ScreenRectangle getRectangle() {
        return new ScreenRectangle(getX(), getY(), getWidth(), getHeight());
    }

    default void setPosition(int x, int y) {
        setX(x);
        setY(y);
    }

    void visitWidgets(Consumer<AbstractWidget> visitor);
}
