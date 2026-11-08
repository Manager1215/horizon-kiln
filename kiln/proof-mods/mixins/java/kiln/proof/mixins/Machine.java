package kiln.proof.mixins;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's own class that its mixins hook inside methods ({@code Machine*Mixin}, D-089): each method's comment says
 * what it does without them. Its code is the mod's, so every point a mixin names is there, as in the game's classes.
 */
public class Machine {
    /** What {@link #log} was given, in order. */
    public final List<String> log = new ArrayList<>();
    /** Read by {@link #power()}. */
    private int power = 5;
    /** Set by the class's static initializer, then by a mixin's. */
    public static int made;

    static {
        made = 1;
    }

    public void log(String line) {
        log.add(line);
    }

    /** {@code v + 1}. */
    public int step(int v) {
        return v + 1;
    }

    /** {@code a * 10 + b}. */
    public int pair(int a, int b) {
        return a * 10 + b;
    }

    /** Logs "a" then "b". */
    public void twoLines() {
        log("a");
        log("b");
    }

    /** {@code step(v)}: {@code v + 1}. */
    public int redirected(int v) {
        return step(v);
    }

    /** Logs "plain". */
    public void argued() {
        log("plain");
    }

    /** {@code pair(a, b)}. */
    public int paired(int a, int b) {
        return pair(a, b);
    }

    /** {@code (x + 1) * 2}. */
    public int variable(int x) {
        int y = x + 1;
        return y * 2;
    }

    /** {@code x}. */
    public int argument(int x) {
        return x;
    }

    /** 42. */
    public int constant() {
        return 42;
    }

    /** {@link #power}: 5. */
    public int power() {
        return power;
    }

    /** {@link #power} too: 5. */
    public int charge() {
        return power;
    }

    /** "xy". */
    public String built() {
        return new StringBuilder("x").append("y").toString();
    }

    /** {@code step(v)}. */
    public int wrapped(int v) {
        return step(v);
    }

    /** {@code step(v) + 1}. */
    public int expression(int v) {
        return step(v) + 1;
    }

    /** Logs "blocked" then "kept". */
    public void conditional() {
        log("blocked");
        log("kept");
    }

    /** {@code x * 2}, logging "local" on the way. */
    public int localed(int x) {
        int y = x * 2;
        log("local");
        return y;
    }

    /** "s". */
    public String shared() {
        return "s";
    }

    /** {@code step(v)}, through a local. */
    public int assigned(int v) {
        int r = step(v);
        return r;
    }

    /** Logs "one", marks, logs "two". */
    public void sliced() {
        log("one");
        mark();
        log("two");
    }

    /** Nothing. */
    public void mark() {}

    /** "kept by the plugin": the plugin's two mixins change it, one of them refused. */
    public String plugged() {
        return "plugged";
    }
}
