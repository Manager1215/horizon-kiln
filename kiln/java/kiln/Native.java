package kiln;

/** What the game gives Java mods directly. */
public final class Native {
    private Native() {}

    /** A line in Kiln's log in the game, as {@code Kiln: [java] message}. */
    public static void log(String message) {
        Host.send("log", message);
    }

    /** A line in the player's chat, after Kiln's name. */
    public static void chat(String message) {
        Host.send("chat", message);
    }
}
