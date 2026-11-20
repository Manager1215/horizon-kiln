package net.minecraft.client.gui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Kiln's stand-in for {@code EqualSpacingLayout}: its children in a row or a column across its whole size, with
 * the same gap between each two (one child sits in the middle); across, each is aligned by its settings.
 */
public class EqualSpacingLayout extends AbstractLayout {
    private final Orientation orientation;
    private final List<ChildContainer> children = new ArrayList<>();
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults();

    public EqualSpacingLayout(int width, int height, Orientation orientation) {
        this(0, 0, width, height, orientation);
    }

    public EqualSpacingLayout(int x, int y, int width, int height, Orientation orientation) {
        super(x, y, width, height);
        this.orientation = orientation;
    }

    @Override
    public void arrangeElements() {
        super.arrangeElements();
        if (children.isEmpty()) {
            return;
        }
        boolean across = orientation == Orientation.HORIZONTAL;
        int used = 0;
        int thickest = 0;
        for (ChildContainer child : children) {
            used += across ? child.getWidth() : child.getHeight();
            thickest = Math.max(thickest, across ? child.getHeight() : child.getWidth());
        }
        int length = across ? width : height;
        int free = Math.max(0, length - used);
        int start = across ? getX() : getY();
        float gap = children.size() == 1 ? 0 : (float) free / (children.size() - 1);
        float at = children.size() == 1 ? start + free / 2.0F : start;
        for (ChildContainer child : children) {
            if (across) {
                child.setX((int) at, child.getWidth());
                child.setY(getY(), height);
                at += child.getWidth() + gap;
            } else {
                child.setY((int) at, child.getHeight());
                child.setX(getX(), width);
                at += child.getHeight() + gap;
            }
        }
        if (across) {
            height = Math.max(height, thickest);
        } else {
            width = Math.max(width, thickest);
        }
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        children.forEach(child -> visitor.accept(child.child));
    }

    @Override
    public void removeChildren() {
        children.clear();
    }

    public LayoutSettings newChildLayoutSettings() {
        return defaultChildLayoutSettings.copy();
    }

    public LayoutSettings defaultChildLayoutSetting() {
        return defaultChildLayoutSettings;
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

    public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    static class ChildContainer extends AbstractLayout.AbstractChildWrapper {
        ChildContainer(LayoutElement child, LayoutSettings settings) {
            super(child, settings);
        }
    }
}
