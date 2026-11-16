package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code AbstractScrollArea}: a widget whose content is taller than it, scrolled by the mouse wheel
 * and by dragging its scrollbar at its right, as vanilla's. Kiln draws the scrollbar with the game's sprites (a grey box
 * where the game has none).
 */
public abstract class AbstractScrollArea extends AbstractWidget {
    public static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_HEIGHT = 32;
    private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("widget/scroller_background");
    private final ScrollbarSettings scrollbarSettings;
    private double scrollAmount;
    private boolean scrolling;

    public AbstractScrollArea(int x, int y, int width, int height, Component message, ScrollbarSettings settings) {
        super(x, y, width, height, message);
        this.scrollbarSettings = settings == null ? defaultSettings(10) : settings;
    }

    /** Kiln's own: for the generated stand-ins that extend it (kiln/java-gen). */
    protected AbstractScrollArea() {
        this.scrollbarSettings = defaultSettings(10);
    }

    /** Vanilla's scrollbar: its sprites, 6 pixels wide, at least 32 high, resized to what of the content shows. */
    public static ScrollbarSettings defaultSettings(int scrollRate) {
        return new ScrollbarSettings(SCROLLER_SPRITE, SCROLLER_SPRITE, SCROLLER_BACKGROUND_SPRITE, SCROLLBAR_WIDTH,
                SCROLLBAR_MIN_HEIGHT, scrollRate, true);
    }

    /** The wheel: up a notch scrolls the content {@link #scrollRate()} pixels up. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!visible) {
            return false;
        }
        setScrollAmount(scrollAmount() - scrollY * scrollRate());
        return true;
    }

    /**
     * Dragging the scrollbar (taken by {@link #updateScrolling}): the content follows the pointer, as far as the bar
     * goes; above or below the area, to the top or the bottom. Otherwise the widget's own drag.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!scrolling) {
            return super.mouseDragged(event, dx, dy);
        }
        if (event.y() < getY()) {
            setScrollAmount(0.0);
        } else if (event.y() > getBottom()) {
            setScrollAmount(maxScrollAmount());
        } else {
            int track = Math.max(1, getHeight() - scrollerHeight());
            setScrollAmount(scrollAmount() + dy * Math.max(1.0, (double) maxScrollAmount() / track));
        }
        return true;
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        scrolling = false;
    }

    public double scrollAmount() {
        return scrollAmount;
    }

    /** How far the content is scrolled, kept between the top and {@link #maxScrollAmount()}. */
    public void setScrollAmount(double amount) {
        scrollAmount = Math.max(0.0, Math.min(amount, maxScrollAmount()));
    }

    /** A press on the scrollbar with the left button starts dragging it; whether it did. */
    public boolean updateScrolling(MouseButtonEvent event) {
        scrolling = scrollable() && isValidClickButton(event.buttonInfo()) && isOverScrollbar(event.x(), event.y());
        return scrolling;
    }

    /** Kiln's own: whether the scrollbar is being dragged. */
    boolean kiln$scrolling() {
        return scrolling;
    }

    protected boolean isOverScrollbar(double x, double y) {
        return x >= scrollBarX() && x < scrollBarX() + scrollbarWidth() && y >= getY() && y < getBottom();
    }

    /** The scroll kept within the content after it changed. */
    public void refreshScrollAmount() {
        setScrollAmount(scrollAmount);
    }

    public int maxScrollAmount() {
        return Math.max(0, contentHeight() - getHeight());
    }

    protected boolean scrollable() {
        return maxScrollAmount() > 0;
    }

    public int scrollbarWidth() {
        return scrollbarSettings.scrollbarWidth();
    }

    /** The bar's height: the share of the content that shows, at least its minimum, at most the area. */
    protected int scrollerHeight() {
        int content = Math.max(1, contentHeight());
        int height = scrollbarSettings.resizingScrollbar() ? (int) ((long) getHeight() * getHeight() / content)
                : scrollbarSettings.scrollbarMinHeight();
        return Math.max(Math.min(scrollbarSettings.scrollbarMinHeight(), getHeight()), Math.min(height, getHeight()));
    }

    protected int scrollBarX() {
        return getRight() - scrollbarWidth();
    }

    /** The bar's top: as far down its track as the content is scrolled. */
    public int scrollBarY() {
        int max = maxScrollAmount();
        return max <= 0 ? getY() : getY() + (int) (scrollAmount * (getHeight() - scrollerHeight()) / max);
    }

    /** The scrollbar, when the content is taller than the area: its track, then the bar. */
    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!scrollable()) {
            return;
        }
        int x = scrollBarX();
        int width = scrollbarWidth();
        sprite(graphics, scrollbarSettings.backgroundSprite(), x, getY(), width, getHeight(), 0xFF000000);
        sprite(graphics, scrollbarSettings.scrollerSprite(), x, scrollBarY(), width, scrollerHeight(), 0xFF808080);
    }

    private static void sprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height,
            int fallback) {
        if (sprite == null) {
            graphics.fill(x, y, x + width, y + height, fallback);
        } else {
            graphics.kiln$sprite(sprite.toString(), x, y, width, height, 1, fallback);
        }
    }

    protected abstract int contentHeight();

    /** Pixels a notch of the wheel scrolls. */
    protected double scrollRate() {
        return scrollbarSettings.scrollRate();
    }

    /** {@code AbstractScrollArea.ScrollbarSettings}: the scrollbar's sprites, size and the wheel's step. */
    public static record ScrollbarSettings(Identifier scrollerSprite, Identifier disabledScrollerSprite,
            Identifier backgroundSprite, int scrollbarWidth, int scrollbarMinHeight, int scrollRate,
            boolean resizingScrollbar) {
        public static final ScrollbarSettings NO_SCROLL = new ScrollbarSettings(null, null, null, 0, 0, 0, false);
    }
}
