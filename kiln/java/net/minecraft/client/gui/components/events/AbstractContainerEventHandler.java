package net.minecraft.client.gui.components.events;

/** Kiln's stand-in for {@code AbstractContainerEventHandler}: the focused child, which alone has the focus, and the drag. */
public abstract class AbstractContainerEventHandler implements ContainerEventHandler {
    private GuiEventListener focused;
    private boolean dragging;

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

    @Override
    public void setFocused(GuiEventListener focused) {
        if (this.focused == focused) {
            return;
        }
        if (this.focused != null) {
            this.focused.setFocused(false);
        }
        if (focused != null) {
            focused.setFocused(true);
        }
        this.focused = focused;
    }
}
