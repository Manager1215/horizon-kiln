package kiln;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The game's events as Java mods hear them. Kiln's Rust side calls the package-private methods. */
public final class Events {
    private static final List<Runnable> TICK = new CopyOnWriteArrayList<>();
    private static long ticks;

    private Events() {}

    /** Runs {@code listener} at the end of every game tick while in a world. */
    public static void onTick(Runnable listener) {
        TICK.add(listener);
    }

    /** Ticks since Kiln started, as of the current tick. */
    public static long ticks() {
        return ticks;
    }

    /** A game tick: every listener runs; one that throws is logged and the others still run. */
    static void tick(long now) {
        ticks = now;
        for (Runnable listener : TICK) {
            try {
                listener.run();
            } catch (Throwable t) {
                Native.log("a tick listener threw " + t);
            }
        }
    }
}
