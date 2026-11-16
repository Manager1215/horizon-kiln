package net.minecraft.client.gui.components;

import net.minecraft.network.chat.Component;

/** Kiln's stand-in for {@code Tooltip}: text shown by a widget while the pointer is over it. */
public class Tooltip {
    private final Component message;

    private Tooltip(Component message) {
        this.message = message;
    }

    public static Tooltip create(Component message) {
        return new Tooltip(message);
    }

    public static Tooltip create(Component message, Component narration) {
        return new Tooltip(message);
    }

    /** Kiln's own: its text. */
    public Component kiln$message() {
        return message;
    }
}
