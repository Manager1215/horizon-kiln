package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;

/** Kiln's stand-in for {@code LinearLayout}: its children in one row or one column, a grid of one line. */
public class LinearLayout implements Layout {
    public final GridLayout wrapped;
    private final Orientation orientation;
    private int nextChildIndex;

    public LinearLayout(int width, int height, Orientation orientation) {
        this.wrapped = new GridLayout(0, 0);
        this.orientation = orientation;
    }

    public static LinearLayout vertical() {
        return new LinearLayout(0, 0, Orientation.VERTICAL);
    }

    public static LinearLayout horizontal() {
        return new LinearLayout(0, 0, Orientation.HORIZONTAL);
    }

    public LinearLayout spacing(int spacing) {
        orientation.setSpacing(wrapped, spacing);
        return this;
    }

    public LayoutSettings newCellSettings() {
        return wrapped.newCellSettings();
    }

    public LayoutSettings defaultCellSetting() {
        return wrapped.defaultCellSetting();
    }

    public <T extends LayoutElement> T addChild(T child, LayoutSettings settings) {
        return orientation.addChild(wrapped, child, nextChildIndex++, settings);
    }

    public <T extends LayoutElement> T addChild(T child) {
        return addChild(child, newCellSettings());
    }

    public <T extends LayoutElement> T addChild(T child, Consumer<LayoutSettings> settings) {
        LayoutSettings made = newCellSettings();
        settings.accept(made);
        return addChild(child, made);
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        wrapped.visitChildren(visitor);
    }

    @Override
    public void removeChildren() {
        wrapped.removeChildren();
        nextChildIndex = 0;
    }

    @Override
    public void arrangeElements() {
        wrapped.arrangeElements();
    }

    @Override
    public int getWidth() {
        return wrapped.getWidth();
    }

    @Override
    public int getHeight() {
        return wrapped.getHeight();
    }

    @Override
    public void setX(int x) {
        wrapped.setX(x);
    }

    @Override
    public void setY(int y) {
        wrapped.setY(y);
    }

    @Override
    public int getX() {
        return wrapped.getX();
    }

    @Override
    public int getY() {
        return wrapped.getY();
    }

    public enum Orientation {
        HORIZONTAL,
        VERTICAL;

        void setSpacing(GridLayout grid, int spacing) {
            if (this == HORIZONTAL) {
                grid.columnSpacing(spacing);
            } else {
                grid.rowSpacing(spacing);
            }
        }

        public <T extends LayoutElement> T addChild(GridLayout grid, T child, int index, LayoutSettings settings) {
            return this == HORIZONTAL ? grid.addChild(child, 0, index, settings) : grid.addChild(child, index, 0, settings);
        }
    }
}
