package net.minecraft.client.gui.layouts;

/**
 * Kiln's stand-in for {@code LayoutSettings}: how a child sits in its cell, its padding on each side and where it
 * aligns (0 left or top, 0.5 the middle, 1 right or bottom).
 */
public interface LayoutSettings {
    LayoutSettings padding(int all);

    LayoutSettings padding(int horizontal, int vertical);

    LayoutSettings padding(int left, int top, int right, int bottom);

    LayoutSettings paddingLeft(int padding);

    LayoutSettings paddingTop(int padding);

    LayoutSettings paddingRight(int padding);

    LayoutSettings paddingBottom(int padding);

    LayoutSettings paddingHorizontal(int padding);

    LayoutSettings paddingVertical(int padding);

    LayoutSettings align(float x, float y);

    LayoutSettings alignHorizontally(float x);

    LayoutSettings alignVertically(float y);

    default LayoutSettings alignHorizontallyLeft() {
        return alignHorizontally(0.0F);
    }

    default LayoutSettings alignHorizontallyCenter() {
        return alignHorizontally(0.5F);
    }

    default LayoutSettings alignHorizontallyRight() {
        return alignHorizontally(1.0F);
    }

    default LayoutSettings alignVerticallyTop() {
        return alignVertically(0.0F);
    }

    default LayoutSettings alignVerticallyMiddle() {
        return alignVertically(0.5F);
    }

    default LayoutSettings alignVerticallyBottom() {
        return alignVertically(1.0F);
    }

    LayoutSettings copy();

    LayoutSettingsImpl getExposed();

    /** New settings: no padding, top left. */
    static LayoutSettings defaults() {
        return new LayoutSettingsImpl();
    }

    class LayoutSettingsImpl implements LayoutSettings {
        public int paddingLeft;
        public int paddingTop;
        public int paddingRight;
        public int paddingBottom;
        public float xAlignment;
        public float yAlignment;

        public LayoutSettingsImpl() {}

        public LayoutSettingsImpl(LayoutSettingsImpl other) {
            paddingLeft = other.paddingLeft;
            paddingTop = other.paddingTop;
            paddingRight = other.paddingRight;
            paddingBottom = other.paddingBottom;
            xAlignment = other.xAlignment;
            yAlignment = other.yAlignment;
        }

        @Override
        public LayoutSettingsImpl padding(int all) {
            return padding(all, all, all, all);
        }

        @Override
        public LayoutSettingsImpl padding(int horizontal, int vertical) {
            return padding(horizontal, vertical, horizontal, vertical);
        }

        @Override
        public LayoutSettingsImpl padding(int left, int top, int right, int bottom) {
            paddingLeft = left;
            paddingTop = top;
            paddingRight = right;
            paddingBottom = bottom;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingLeft(int padding) {
            paddingLeft = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingTop(int padding) {
            paddingTop = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingRight(int padding) {
            paddingRight = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingBottom(int padding) {
            paddingBottom = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingHorizontal(int padding) {
            paddingLeft = padding;
            paddingRight = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingVertical(int padding) {
            paddingTop = padding;
            paddingBottom = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl align(float x, float y) {
            xAlignment = x;
            yAlignment = y;
            return this;
        }

        @Override
        public LayoutSettingsImpl alignHorizontally(float x) {
            xAlignment = x;
            return this;
        }

        @Override
        public LayoutSettingsImpl alignVertically(float y) {
            yAlignment = y;
            return this;
        }

        @Override
        public LayoutSettingsImpl copy() {
            return new LayoutSettingsImpl(this);
        }

        @Override
        public LayoutSettingsImpl getExposed() {
            return this;
        }
    }
}
