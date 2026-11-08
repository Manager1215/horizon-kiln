package kiln.proof.mixins;

/** The mod's own class with nothing public but {@link #greet()}: its mixin (SecretAccessor) reaches the rest. */
public class Secret {
    private static final String GREETING = String.valueOf("hello");
    private final String word;

    private Secret(String word) {
        this.word = word;
    }

    private String shout(String suffix) {
        return word.toUpperCase(java.util.Locale.ROOT) + suffix;
    }

    private static int twice(int n) {
        return 2 * n;
    }

    public String greet() {
        return GREETING + ", " + word;
    }
}
