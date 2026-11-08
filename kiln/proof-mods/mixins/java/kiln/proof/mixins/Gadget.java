package kiln.proof.mixins;

/**
 * The mod's own class that its whole-method mixins change ({@code Gadget*Mixin}): each method's comment says what it
 * does without them.
 */
public class Gadget {
    private final String name;
    /** Bumped by {@link #touch()}. */
    public int touched;

    public Gadget(String name) {
        this.name = name;
    }

    /** Calls the other constructor: Mixin puts the field initializers only in that one. */
    public Gadget() {
        this("plain");
    }

    /** "label NAME". */
    public String label() {
        return "label " + name;
    }

    /** "secret". */
    private String secret() {
        return "secret";
    }

    /** What {@link #secret()} gives. */
    public String reveal() {
        return secret();
    }

    /** "describe NAME". */
    public String describe() {
        return "describe " + name;
    }

    /** {@code 2 n}. */
    public static int twice(int n) {
        return 2 * n;
    }

    /** 4. */
    public int size() {
        return 4;
    }

    /** {@code a + b}. */
    public long total(long a, int b) {
        return a + b;
    }

    /** -1 for a negative {@code x} (an early return), {@code x / 2} at its last. */
    public double ratio(double x) {
        if (x < 0) {
            return -1;
        }
        return x / 2;
    }

    /** "WHO xTIMES". */
    public String greet(String who, int times) {
        return who + " x" + times;
    }

    /** {@code v * by}. */
    public static long scale(long v, double by) {
        return (long) (v * by);
    }

    /** {@link #touched} once more. */
    public void touch() {
        touched++;
    }

    /** {@code a + b}. */
    public int sum(int a, int b) {
        return a + b;
    }
}
