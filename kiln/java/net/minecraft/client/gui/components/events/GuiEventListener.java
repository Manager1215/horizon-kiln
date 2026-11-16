package net.minecraft.client.gui.components.events;

import net.minecraft.client.gui.components.TabOrderedElement;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Kiln's stand-in for {@code GuiEventListener}: what a screen's mouse, keys and typing reach, as vanilla's. */
public interface GuiEventListener extends TabOrderedElement {
    default void mouseMoved(double x, double y) {}

    default boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    default boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    default boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return false;
    }

    default boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return false;
    }

    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    default boolean keyReleased(KeyEvent event) {
        return false;
    }

    default boolean charTyped(CharacterEvent event) {
        return false;
    }

    default boolean isMouseOver(double x, double y) {
        return false;
    }

    void setFocused(boolean focused);

    boolean isFocused();

    default boolean shouldTakeFocusAfterInteraction() {
        return true;
    }

    default boolean capturesInput() {
        return false;
    }
}
