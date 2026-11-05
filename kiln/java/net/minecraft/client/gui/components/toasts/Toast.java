package net.minecraft.client.gui.components.toasts;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Kiln's stand-in for {@code Toast}: a notice sliding in at the top right. The game draws its own toasts; Kiln shows a
 * mod's {@link SystemToast} as one of them, and no toast a mod draws itself.
 */
public interface Toast {
    Object NO_TOKEN = new Object();
    int DEFAULT_WIDTH = 160;
    int SLOT_HEIGHT = 32;

    Visibility getWantedVisibility();

    void update(ToastManager manager, long fullyVisibleForMs);

    void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs);

    default Object getToken() {
        return NO_TOKEN;
    }

    default int width() {
        return DEFAULT_WIDTH;
    }

    default int height() {
        return SLOT_HEIGHT;
    }

    enum Visibility {
        SHOW,
        HIDE
    }
}
