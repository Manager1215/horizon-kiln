package net.minecraft.client.gui.navigation;

/** Kiln's stand-in for {@code ScreenRectangle}: a box on the screen in GUI pixels, its top left and its size. */
public record ScreenRectangle(ScreenPosition position, int width, int height) {
    private static final ScreenRectangle EMPTY = new ScreenRectangle(0, 0, 0, 0);

    public ScreenRectangle(int x, int y, int width, int height) {
        this(new ScreenPosition(x, y), width, height);
    }

    public static ScreenRectangle empty() {
        return EMPTY;
    }

    public int left() {
        return position.x();
    }

    public int top() {
        return position.y();
    }

    public int right() {
        return position.x() + width;
    }

    public int bottom() {
        return position.y() + height;
    }

    public boolean containsPoint(int x, int y) {
        return x >= left() && x < right() && y >= top() && y < bottom();
    }

    public boolean overlaps(ScreenRectangle other) {
        return left() < other.right() && right() > other.left() && top() < other.bottom() && bottom() > other.top();
    }

    public boolean encompasses(ScreenRectangle other) {
        return other.left() >= left() && other.top() >= top() && other.right() <= right()
                && other.bottom() <= bottom();
    }

    /** The box both cover, or null where they don't meet. */
    public ScreenRectangle intersection(ScreenRectangle other) {
        int x0 = Math.max(left(), other.left());
        int y0 = Math.max(top(), other.top());
        int x1 = Math.min(right(), other.right());
        int y1 = Math.min(bottom(), other.bottom());
        return x0 < x1 && y0 < y1 ? new ScreenRectangle(x0, y0, x1 - x0, y1 - y0) : null;
    }

    /** The smallest box around this one moved and scaled by {@code pose}. */
    public ScreenRectangle transformMaxBounds(org.joml.Matrix3x2fc pose) {
        org.joml.Vector2f a = pose.transformPosition(left(), top(), new org.joml.Vector2f());
        org.joml.Vector2f b = pose.transformPosition(right(), bottom(), new org.joml.Vector2f());
        org.joml.Vector2f c = pose.transformPosition(left(), bottom(), new org.joml.Vector2f());
        org.joml.Vector2f d = pose.transformPosition(right(), top(), new org.joml.Vector2f());
        float x0 = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        float y0 = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        float x1 = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        float y1 = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));
        int left = (int) Math.floor(x0);
        int top = (int) Math.floor(y0);
        return new ScreenRectangle(left, top, (int) Math.ceil(x1) - left, (int) Math.ceil(y1) - top);
    }

    public ScreenRectangle transformAxisAligned(org.joml.Matrix3x2fc pose) {
        return transformMaxBounds(pose);
    }
}
