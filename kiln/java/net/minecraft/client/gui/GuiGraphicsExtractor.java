package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Kiln's stand-in for {@code GuiGraphicsExtractor}: it records what a HUD element draws, and Kiln's Rust side
 * draws the same with Horizon's HUD every frame until the next tick. Colours are ARGB; alpha 0 draws nothing,
 * as in vanilla.
 */
public class GuiGraphicsExtractor {
    private final int width;
    private final int height;
    private final List<String> ops = new ArrayList<>();

    /** Kiln's own: a canvas of the game's GUI size. */
    public GuiGraphicsExtractor(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int guiWidth() {
        return width;
    }

    public int guiHeight() {
        return height;
    }

    /** The box between corners (x1, y1) and (x2, y2). */
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if (argb >>> 24 == 0) {
            return;
        }
        int x = Math.min(x1, x2);
        int y = Math.min(y1, y2);
        ops.add("f " + x + " " + y + " " + Math.abs(x2 - x1) + " " + Math.abs(y2 - y1) + " " + Integer.toUnsignedString(argb));
    }

    /** {@code text} with its top-left at (x, y), with a shadow. */
    public void text(Font font, String text, int x, int y, int argb) {
        text(font, text, x, y, argb, true);
    }

    public void text(Font font, String text, int x, int y, int argb, boolean shadow) {
        if (argb >>> 24 == 0 || text == null || text.isEmpty()) {
            return;
        }
        ops.add("t " + x + " " + y + " " + Integer.toUnsignedString(argb) + " " + (shadow ? 1 : 0) + " " + text);
    }

    /** Kiln's own: what was drawn, one op a string ({@code f x y w h argb}, {@code t x y argb shadow text}). */
    public List<String> kiln$ops() {
        return ops;
    }
}
