package kiln;

/** What Kiln's Rust side gives Java code: registered with RegisterNatives when the JVM starts. */
public final class Native {
    private Native() {}

    /** A line in the game's log. */
    public static native void log(String message);

    /** The game's tick counter, as Rust keeps it. */
    public static native long gameTicks();
}
