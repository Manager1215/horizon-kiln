package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code AbstractSelectionList}: a scrolling list of rows (entries), each as tall as it was added
 * ({@link #addEntry(Entry, int)}), stacked in a column {@link #getRowWidth()} wide in the middle of the list. A click
 * goes to the row under the pointer, which gets the focus (and, where rows can be, is selected); the wheel and the
 * scrollbar scroll it; the up and down keys move the focus a row, scrolled into view. Each row draws itself
 * ({@link Entry#extractContent}), cut to the list.
 *
 * <p>Left out: narration (Kiln has no narrator), vanilla's focus paths ({@code nextFocusPath}: Kiln's screens move the
 * focus with Tab and the arrows here), {@code updateSize} with a {@code HeaderAndFooterLayout} and an entry's
 * {@code getRectangle}. The list's ground and separators are plain boxes, not vanilla's textures.
 */
public abstract class AbstractSelectionList<E extends AbstractSelectionList.Entry<E>> extends AbstractContainerWidget {
    public static final Identifier INWORLD_MENU_LIST_BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
    /** Above the first row and below the last, inside the list. */
    private static final int LIST_PADDING = 4;
    protected final Minecraft minecraft;
    protected final int defaultEntryHeight;
    private final List<E> children = new ArrayList<>();
    protected boolean centerListVertically = true;
    private E selected;
    private E hovered;

    public AbstractSelectionList(Minecraft minecraft, int width, int height, int y, int defaultEntryHeight) {
        super(0, y, width, height, Component.empty(), AbstractScrollArea.defaultSettings(defaultEntryHeight / 2));
        this.minecraft = minecraft;
        this.defaultEntryHeight = defaultEntryHeight;
    }

    /** Kiln's own: for the generated stand-ins that extend it (kiln/java-gen). */
    protected AbstractSelectionList() {
        this(Minecraft.getInstance(), 0, 0, 0, 0);
    }

    public E getSelected() {
        return selected;
    }

    public void setSelected(E entry) {
        selected = entry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E getFocused() {
        return (E) super.getFocused();
    }

    /** Its rows: the list itself, so a mod's {@code children().add} adds a row (laid out when next drawn). */
    @Override
    public final List<E> children() {
        return children;
    }

    protected void sort(Comparator<E> order) {
        children.sort(order);
        repositionEntries();
    }

    protected void swap(int a, int b) {
        java.util.Collections.swap(children, a, b);
        repositionEntries();
        refreshScrollAmount();
    }

    protected void clearEntries() {
        children.clear();
        selected = null;
        hovered = null;
        setFocused((GuiEventListener) null);
        refreshScrollAmount();
    }

    protected void clearEntriesExcept(E kept) {
        children.removeIf(entry -> entry != kept);
        if (selected != kept) {
            selected = null;
        }
        repositionEntries();
        refreshScrollAmount();
    }

    public void replaceEntries(Collection<E> entries) {
        clearEntries();
        for (E entry : entries) {
            addEntry(entry);
        }
    }

    /** Where a row added now would start: under the last. */
    public int getNextY() {
        int y = getY() + LIST_PADDING - (int) scrollAmount();
        for (E entry : children) {
            y += entry.getHeight();
        }
        return y;
    }

    protected int addEntry(E entry) {
        return addEntry(entry, defaultEntryHeight);
    }

    /** A row {@code height} pixels tall at the bottom; its index. */
    protected int addEntry(E entry, int height) {
        entry.setHeight(height);
        children.add(entry);
        repositionEntries();
        return children.size() - 1;
    }

    protected void addEntryToTop(E entry) {
        addEntryToTop(entry, defaultEntryHeight);
    }

    /** A row at the top; what showed stays where it was (the scroll grows by the row). */
    protected void addEntryToTop(E entry, int height) {
        double scroll = maxScrollAmount() - scrollAmount();
        entry.setHeight(height);
        children.add(0, entry);
        repositionEntries();
        setScrollAmount(maxScrollAmount() - scroll);
    }

    protected void removeEntryFromTop(E entry) {
        double scroll = maxScrollAmount() - scrollAmount();
        removeEntry(entry);
        setScrollAmount(maxScrollAmount() - scroll);
    }

    protected int getItemCount() {
        return children.size();
    }

    /** Whether a row clicked or reached by the keys is selected too (a list of buttons' rows isn't). */
    protected boolean entriesCanBeSelected() {
        return true;
    }

    /** The row at (x, y), if the point is in the rows' column and inside the list. */
    protected final E getEntryAtPosition(double x, double y) {
        if (x < getRowLeft() || x >= getRowRight() || y < getY() || y >= getBottom()) {
            return null;
        }
        repositionEntries();
        for (E entry : children) {
            if (y >= entry.getY() && y < entry.getY() + entry.getHeight()) {
                return entry;
            }
        }
        return null;
    }

    public void updateSizeAndPosition(int width, int height, int y) {
        updateSizeAndPosition(width, height, 0, y);
    }

    public void updateSizeAndPosition(int width, int height, int x, int y) {
        setRectangle(width, height, x, y);
        repositionEntries();
        refreshScrollAmount();
    }

    /** The rows' heights and the padding above and below them. */
    @Override
    protected int contentHeight() {
        int height = 2 * LIST_PADDING;
        for (E entry : children) {
            height += entry.getHeight();
        }
        return height;
    }

    /** Its ground, its rows cut to it, the separators above and below, then the scrollbar. */
    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        repositionEntries();
        hovered = isMouseOver(mouseX, mouseY) ? getEntryAtPosition(mouseX, mouseY) : null;
        extractListBackground(graphics);
        enableScissor(graphics);
        extractListItems(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        extractListSeparators(graphics);
        extractScrollbar(graphics, mouseX, mouseY);
    }

    /** Lines along its top and bottom edges, as vanilla's separators lie. */
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        graphics.fill(getX(), getY() - 2, getRight(), getY() - 1, 0x40FFFFFF);
        graphics.fill(getX(), getY() - 1, getRight(), getY(), 0xBF000000);
        graphics.fill(getX(), getBottom(), getRight(), getBottom() + 1, 0xBF000000);
        graphics.fill(getX(), getBottom() + 1, getRight(), getBottom() + 2, 0x40FFFFFF);
    }

    /** A dark ground under the rows. */
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0x40000000);
    }

    protected void enableScissor(GuiGraphicsExtractor graphics) {
        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
    }

    /** Scrolled so the whole row shows, as little as it takes; to the top for the first row, the end for the last. */
    protected void scrollToEntry(E entry) {
        repositionEntries();
        int at = children.indexOf(entry);
        if (at == 0) {
            setScrollAmount(0.0);
            return;
        }
        if (at == children.size() - 1) {
            setScrollAmount(maxScrollAmount());
            return;
        }
        int top = entry.getY() - getY();
        int bottom = entry.getY() + entry.getHeight() - getBottom();
        if (top < 0) {
            setScrollAmount(scrollAmount() + top);
        } else if (bottom > 0) {
            setScrollAmount(scrollAmount() + bottom);
        }
    }

    /** Scrolled so the row is in the list's middle. */
    protected void centerScrollOn(E entry) {
        repositionEntries();
        double contentTop = entry.getY() + scrollAmount() - getY();
        setScrollAmount(contentTop + entry.getHeight() / 2.0 - getHeight() / 2.0);
    }

    /** The scroll moves the rows with it. */
    @Override
    public void setScrollAmount(double amount) {
        super.setScrollAmount(amount);
        repositionEntries();
    }

    /** Right of the rows, inside the list. */
    @Override
    protected int scrollBarX() {
        return Math.min(getRowRight() + 4, getRight() - scrollbarWidth());
    }

    @Override
    public Optional<GuiEventListener> getChildAt(double x, double y) {
        return Optional.ofNullable(getEntryAtPosition(x, y));
    }

    /** The focus to a row of the list (null for none); where rows can be selected, that row is. */
    @Override
    public void setFocused(GuiEventListener child) {
        super.setFocused(child);
        int at = child == null ? -1 : children.indexOf(child);
        if (at >= 0 && entriesCanBeSelected()) {
            setSelected(children.get(at));
        }
    }

    /**
     * A press: on the scrollbar, dragging it; on a row, to the row (which takes the focus and the drag if it took
     * the press). A row of another kind whose child had the focus loses it.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (updateScrolling(event)) {
            return true;
        }
        E entry = getEntryAtPosition(event.x(), event.y());
        if (entry == null || !entry.mouseClicked(event, doubleClick)) {
            return false;
        }
        E before = getFocused();
        if (before != entry && before instanceof ContainerEventHandler container) {
            container.setFocused((GuiEventListener) null);
        }
        setFocused(entry);
        setDragging(true);
        return true;
    }

    /** A key: to the focused row first; else up and down move the focus a row, scrolled into view. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        E focused = getFocused();
        if (focused != null && focused.keyPressed(event)) {
            return true;
        }
        if (!event.isUp() && !event.isDown()) {
            return false;
        }
        E from = focused != null ? focused : getSelected();
        E next = nextEntry(event.isUp() ? ScreenDirection.UP : ScreenDirection.DOWN, entry -> true, from);
        if (next == null) {
            return false;
        }
        setFocused(next);
        scrollToEntry(next);
        return true;
    }

    protected E nextEntry(ScreenDirection direction) {
        return nextEntry(direction, entry -> true);
    }

    protected E nextEntry(ScreenDirection direction, Predicate<E> accepts) {
        return nextEntry(direction, accepts, getSelected());
    }

    /** The first row from {@code from} up or down that {@code accepts} takes: from the top or bottom with none. */
    protected E nextEntry(ScreenDirection direction, Predicate<E> accepts, E from) {
        int step = direction == ScreenDirection.UP ? -1 : direction == ScreenDirection.DOWN ? 1 : 0;
        if (step == 0 || children.isEmpty()) {
            return null;
        }
        int at = children.indexOf(from);
        int start = at < 0 ? (step > 0 ? 0 : children.size() - 1) : at + step;
        for (int i = start; i >= 0 && i < children.size(); i += step) {
            if (accepts.test(children.get(i))) {
                return children.get(i);
            }
        }
        return null;
    }

    /** The rows that show, at least partly. */
    protected void extractListItems(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        for (E entry : children) {
            if (entry.getY() + entry.getHeight() > getY() && entry.getY() < getBottom()) {
                extractItem(graphics, mouseX, mouseY, partialTick, entry);
            }
        }
    }

    /** A row: framed while selected (white while the list has the focus, grey else), then its content. */
    protected void extractItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, E entry) {
        if (entriesCanBeSelected() && getSelected() == entry) {
            extractSelection(graphics, entry, isFocused() ? 0xFFFFFFFF : 0xFF808080);
        }
        entry.extractContent(graphics, mouseX, mouseY, entry == hovered, partialTick);
    }

    /** A frame of {@code outlineColor} round the row, black inside. */
    protected void extractSelection(GuiGraphicsExtractor graphics, E entry, int outlineColor) {
        int left = entry.getX();
        int top = entry.getY();
        int right = left + entry.getWidth();
        int bottom = top + entry.getHeight();
        graphics.fill(left, top, right, bottom, outlineColor);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF000000);
    }

    public int getRowLeft() {
        return getX() + getWidth() / 2 - getRowWidth() / 2;
    }

    public int getRowRight() {
        return getRowLeft() + getRowWidth();
    }

    public int getRowTop(int index) {
        repositionEntries();
        return children.get(index).getY();
    }

    public int getRowBottom(int index) {
        repositionEntries();
        E entry = children.get(index);
        return entry.getY() + entry.getHeight();
    }

    /** The rows' width, the same for all; vanilla's 220 pixels. */
    public int getRowWidth() {
        return 220;
    }

    protected void removeEntries(List<E> entries) {
        for (E entry : List.copyOf(entries)) {
            removeEntry(entry);
        }
    }

    protected void removeEntry(E entry) {
        if (children.remove(entry)) {
            if (selected == entry) {
                selected = null;
            }
            if (hovered == entry) {
                hovered = null;
            }
            if (getFocused() == entry) {
                setFocused((GuiEventListener) null);
            }
            repositionEntries();
            refreshScrollAmount();
        }
    }

    protected E getHovered() {
        return hovered;
    }

    /**
     * Kiln's own: each row placed where it shows now, under the one before, scrolled; the rows' column centred when
     * {@link #centerListVertically} and they are shorter than the list. A row added through {@code children()} gets
     * the list's height for a row.
     */
    private void repositionEntries() {
        int content = contentHeight();
        int y = getY() + LIST_PADDING - (int) scrollAmount();
        if (centerListVertically && content < getHeight()) {
            y += (getHeight() - content) / 2;
        }
        for (E entry : children) {
            if (entry.getHeight() <= 0) {
                entry.setHeight(defaultEntryHeight);
            }
            entry.setX(getRowLeft());
            entry.setY(y);
            entry.setWidth(getRowWidth());
            y += entry.getHeight();
        }
    }

    /**
     * {@code AbstractSelectionList.Entry}: a row, placed and sized by its list. Its content is inside it by
     * {@link #CONTENT_PADDING} each side; it draws itself there ({@link #extractContent}).
     */
    public abstract static class Entry<E extends Entry<E>> implements LayoutElement, GuiEventListener {
        public static final int CONTENT_PADDING = 2;
        private int x;
        private int y;
        private int width;
        private int height;
        private boolean focused;

        protected Entry() {}

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        public boolean isFocused() {
            return focused;
        }

        /** The row's content, drawn where it is now ({@link #getContentX()}, ...); {@code hovered}: the pointer is on it. */
        public abstract void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                float partialTick);

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        @Override
        public void setX(int x) {
            this.x = x;
        }

        @Override
        public void setY(int y) {
            this.y = y;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getContentX() {
            return x + CONTENT_PADDING;
        }

        public int getContentY() {
            return y + CONTENT_PADDING;
        }

        public int getContentHeight() {
            return height - 2 * CONTENT_PADDING;
        }

        public int getContentYMiddle() {
            return getContentY() + getContentHeight() / 2;
        }

        public int getContentBottom() {
            return getContentY() + getContentHeight();
        }

        public int getContentWidth() {
            return width - 2 * CONTENT_PADDING;
        }

        public int getContentXMiddle() {
            return getContentX() + getContentWidth() / 2;
        }

        public int getContentRight() {
            return getContentX() + getContentWidth();
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

        @Override
        public void visitWidgets(Consumer<AbstractWidget> visitor) {}
    }
}
