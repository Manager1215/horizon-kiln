package net.minecraft.client.gui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * Kiln's stand-in for {@code FrameLayout}: its children on top of each other in one box, as big as the biggest
 * child or its least size, each aligned in it by its settings.
 */
public class FrameLayout extends AbstractLayout {
    public final List<ChildContainer> children = new ArrayList<>();
    private int minWidth;
    private int minHeight;
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults().align(0.5F, 0.5F);

    public FrameLayout() {
        this(0, 0, 0, 0);
    }

    public FrameLayout(int width, int height) {
        this(0, 0, width, height);
    }

    public FrameLayout(int x, int y, int width, int height) {
        super(x, y, width, height);
        setMinDimensions(width, height);
    }

    public FrameLayout setMinDimensions(int width, int height) {
        return setMinWidth(width).setMinHeight(height);
    }

    public FrameLayout setMinHeight(int height) {
        this.minHeight = height;
        return this;
    }

    public FrameLayout setMinWidth(int width) {
        this.minWidth = width;
        return this;
    }

    public LayoutSettings newChildLayoutSettings() {
        return defaultChildLayoutSettings.copy();
    }

    public LayoutSettings defaultChildLayoutSetting() {
        return defaultChildLayoutSettings;
    }

    @Override
    public void arrangeElements() {
        super.arrangeElements();
        int w = minWidth;
        int h = minHeight;
        for (ChildContainer child : children) {
            w = Math.max(w, child.getWidth());
            h = Math.max(h, child.getHeight());
        }
        for (ChildContainer child : children) {
            child.setX(getX(), w);
            child.setY(getY(), h);
        }
        width = w;
        height = h;
    }

    public <T extends LayoutElement> T addChild(T child) {
        return addChild(child, newChildLayoutSettings());
    }

    public <T extends LayoutElement> T addChild(T child, LayoutSettings settings) {
        children.add(new ChildContainer(child, settings));
        return child;
    }

    public <T extends LayoutElement> T addChild(T child, Consumer<LayoutSettings> settings) {
        LayoutSettings made = newChildLayoutSettings();
        settings.accept(made);
        return addChild(child, made);
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        children.forEach(child -> visitor.accept(child.child));
    }

    @Override
    public void removeChildren() {
        children.clear();
    }

    public static void centerInRectangle(LayoutElement element, int x, int y, int width, int height) {
        alignInRectangle(element, x, y, width, height, 0.5F, 0.5F);
    }

    public static void centerInRectangle(LayoutElement element, ScreenRectangle rectangle) {
        centerInRectangle(element, rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height());
    }

    public static void alignInRectangle(LayoutElement element, ScreenRectangle rectangle, float x, float y) {
        alignInRectangle(element, rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height(), x, y);
    }

    public static void alignInRectangle(LayoutElement element, int x, int y, int width, int height, float alignX,
            float alignY) {
        alignInDimension(x, width, element.getWidth(), element::setX, alignX);
        alignInDimension(y, height, element.getHeight(), element::setY, alignY);
    }

    /** Places something {@code size} long in a span from {@code start}, {@code span} long, at {@code align} of it. */
    public static void alignInDimension(int start, int span, int size, Consumer<Integer> place, float align) {
        place.accept(start + (int) ((span - size) * align));
    }

    public static class ChildContainer extends AbstractLayout.AbstractChildWrapper {
        protected ChildContainer(LayoutElement child, LayoutSettings settings) {
            super(child, settings);
        }

        protected ChildContainer() {
            super();
        }
    }
}
