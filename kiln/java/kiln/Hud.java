package kiln;

/** A line of text that Kiln shows in its HUD box, under its status. */
public final class Hud {
    private static volatile String line = "";

    private Hud() {}

    /** Replaces the line ({@code null} or empty: no line). */
    public static void line(String text) {
        line = text == null ? "" : text;
    }

    /** Read by Kiln's Rust side once a tick. */
    static String text() {
        return line;
    }
}
