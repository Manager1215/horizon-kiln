package net.minecraft.client.renderer;

/** Kiln's stand-in for {@code Rect2i}: a box of whole pixels that can be moved and resized. */
public class Rect2i {
    private int x;
    private int y;
    private int width;
    private int height;

    public Rect2i(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rect2i intersect(Rect2i other) {
        int x0 = Math.max(x, other.x);
        int y0 = Math.max(y, other.y);
        int x1 = Math.min(x + width, other.x + other.width);
        int y1 = Math.min(y + height, other.y + other.height);
        x = x0;
        y = y0;
        width = Math.max(0, x1 - x0);
        height = Math.max(0, y1 - y0);
        return this;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean contains(int px, int py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
}
