package net.minecraft.client.gui;

/** Kiln's stand-in for {@code Font}: text widths as Horizon measures them (Kiln sends a table once). */
public class Font {
    public final int lineHeight = 9;
    private int[] widths = new int[0];

    /** Width of {@code text} in GUI pixels. Characters Kiln has no width for count as 6. */
    public int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += c < widths.length && widths[c] > 0 ? widths[c] : 6;
        }
        return w;
    }

    /** Kiln's own: widths by character, from the game's font. */
    public void kiln$widths(int[] widths) {
        this.widths = widths.clone();
    }
}
