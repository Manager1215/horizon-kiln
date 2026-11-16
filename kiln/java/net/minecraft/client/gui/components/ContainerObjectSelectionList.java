package net.minecraft.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Kiln's stand-in for {@code ContainerObjectSelectionList}: a list whose rows hold widgets (buttons, switches, edit
 * boxes), which get the mouse and keys as a screen's do; a row isn't selected, the widget in it is focused. A row
 * places and draws its widgets in its {@link Entry#extractContent}, as vanilla's do, so a click finds them where they
 * were last drawn. Narration and focus paths are left out.
 */
public abstract class ContainerObjectSelectionList<E extends ContainerObjectSelectionList.Entry<E>>
        extends AbstractSelectionList<E> {
    public ContainerObjectSelectionList(Minecraft minecraft, int width, int height, int y, int defaultEntryHeight) {
        super(minecraft, width, height, y, defaultEntryHeight);
    }

    /** The focus to a row; the row before loses its focused widget. */
    @Override
    public void setFocused(GuiEventListener child) {
        GuiEventListener before = getFocused();
        super.setFocused(child);
        if (before != child && before instanceof ContainerEventHandler container) {
            container.setFocused((GuiEventListener) null);
        }
    }

    @Override
    protected boolean entriesCanBeSelected() {
        return false;
    }

    /** {@code ContainerObjectSelectionList.Entry}: a row of widgets ({@code children()}), the focused one and the drag. */
    public abstract static class Entry<E extends Entry<E>> extends AbstractSelectionList.Entry<E>
            implements ContainerEventHandler {
        private GuiEventListener focused;
        private boolean dragging;

        public Entry() {}

        @Override
        public boolean isDragging() {
            return dragging;
        }

        @Override
        public void setDragging(boolean dragging) {
            this.dragging = dragging;
        }

        /** A press to the widget under the pointer, which takes the focus. */
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return ContainerEventHandler.super.mouseClicked(event, doubleClick);
        }

        @Override
        public void setFocused(GuiEventListener child) {
            if (focused == child) {
                return;
            }
            if (focused != null) {
                focused.setFocused(false);
            }
            if (child != null) {
                child.setFocused(true);
            }
            focused = child;
        }

        @Override
        public GuiEventListener getFocused() {
            return focused;
        }

        /** The row losing the focus, its widget loses it too. */
        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) {
                setFocused((GuiEventListener) null);
            }
        }

        @Override
        public boolean isFocused() {
            return super.isFocused();
        }
    }
}
