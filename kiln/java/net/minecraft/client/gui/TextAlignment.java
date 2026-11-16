package net.minecraft.client.gui;

import net.minecraft.util.FormattedCharSequence;

/** Kiln's stand-in for {@code TextAlignment}: where a line of text starts for its anchor x. */
public enum TextAlignment {
    LEFT,
    CENTER,
    RIGHT;

    public int calculateLeft(int anchor, int width) {
        return switch (this) {
            case LEFT -> anchor;
            case CENTER -> anchor - width / 2;
            case RIGHT -> anchor - width;
        };
    }

    public int calculateLeft(int anchor, Font font, FormattedCharSequence text) {
        return calculateLeft(anchor, font.width(text));
    }
}
