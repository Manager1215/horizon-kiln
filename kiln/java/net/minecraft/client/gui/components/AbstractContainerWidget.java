package net.minecraft.client.gui.components;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Kiln's stand-in for {@code AbstractContainerWidget}: a scroll area of children, which get the mouse and keys as a
 * screen's do ({@link ContainerEventHandler}): the focused one, and the drag. Dragging its scrollbar comes first.
 * Vanilla's focus paths ({@code nextFocusPath}) are left out: Kiln's screens move the focus with Tab only.
 */
public abstract class AbstractContainerWidget extends AbstractScrollArea implements ContainerEventHandler {
    private GuiEventListener focused;
    private boolean dragging;

    public AbstractContainerWidget(int x, int y, int width, int height, Component message) {
        this(x, y, width, height, message, AbstractScrollArea.defaultSettings(10));
    }

    public AbstractContainerWidget(int x, int y, int width, int height, Component message,
            AbstractScrollArea.ScrollbarSettings settings) {
        super(x, y, width, height, message, settings);
    }

    /** Kiln's own: for the generated stand-ins that extend it (kiln/java-gen). */
    protected AbstractContainerWidget() {}

    @Override
    public final boolean isDragging() {
        return dragging;
    }

    @Override
    public final void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    @Override
    public GuiEventListener getFocused() {
        return focused;
    }

    /** The child that has the focus, alone: the one before loses it. */
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        return updateScrolling(event) || ContainerEventHandler.super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean scrolled = kiln$scrolling();
        if (isValidClickButton(event.buttonInfo())) {
            onRelease(event);
        }
        return ContainerEventHandler.super.mouseReleased(event) || scrolled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (kiln$scrolling()) {
            return super.mouseDragged(event, dx, dy);
        }
        return ContainerEventHandler.super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return ContainerEventHandler.super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        return ContainerEventHandler.super.keyReleased(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return ContainerEventHandler.super.charTyped(event);
    }

    /** Losing the focus, its focused child loses it too. */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            setFocused((GuiEventListener) null);
        }
    }
}
