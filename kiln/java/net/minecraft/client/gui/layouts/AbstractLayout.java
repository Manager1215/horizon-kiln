package net.minecraft.client.gui.layouts;

/**
 * Kiln's stand-in for {@code AbstractLayout}: a layout with a place and a size of its own. Moving it moves its
 * children by as much.
 */
public abstract class AbstractLayout implements Layout {
    private int x;
    private int y;
    protected int width;
    protected int height;

    public AbstractLayout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Kiln's own: a layout at (0, 0) with no size yet. */
    protected AbstractLayout() {
        this(0, 0, 0, 0);
    }

    @Override
    public void setX(int x) {
        int by = x - this.x;
        this.x = x;
        visitChildren(child -> child.setX(child.getX() + by));
    }

    @Override
    public void setY(int y) {
        int by = y - this.y;
        this.y = y;
        visitChildren(child -> child.setY(child.getY() + by));
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    /** A child and its settings: its size with its padding, and how it is placed in a cell. */
    public abstract static class AbstractChildWrapper {
        public final LayoutElement child;
        public final LayoutSettings.LayoutSettingsImpl layoutSettings;

        protected AbstractChildWrapper(LayoutElement child, LayoutSettings settings) {
            this.child = child;
            this.layoutSettings = settings.getExposed();
        }

        /** Kiln's own: a wrapper of nothing (a generated subclass's). */
        protected AbstractChildWrapper() {
            this(new SpacerElement(0, 0), LayoutSettings.defaults());
        }

        public int getHeight() {
            return child.getHeight() + layoutSettings.paddingTop + layoutSettings.paddingBottom;
        }

        public int getWidth() {
            return child.getWidth() + layoutSettings.paddingLeft + layoutSettings.paddingRight;
        }

        /** Puts the child in a cell from {@code x}, {@code size} wide, by its padding and alignment. */
        public void setX(int x, int size) {
            float inner = size - layoutSettings.paddingLeft - layoutSettings.paddingRight;
            FrameLayout.alignInDimension(x + layoutSettings.paddingLeft, (int) inner, child.getWidth(), child::setX,
                    layoutSettings.xAlignment);
        }

        public void setY(int y, int size) {
            float inner = size - layoutSettings.paddingTop - layoutSettings.paddingBottom;
            FrameLayout.alignInDimension(y + layoutSettings.paddingTop, (int) inner, child.getHeight(), child::setY,
                    layoutSettings.yAlignment);
        }
    }
}
