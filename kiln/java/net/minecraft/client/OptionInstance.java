package net.minecraft.client;

/**
 * Kiln's stand-in for {@code OptionInstance}: one of the game's options and its value, as the game said. A mod's
 * {@code set} of one Horizon has goes back to it (kiln.Back, D-105); of one it hasn't, it stays in Java.
 */
public class OptionInstance<T> {
    private final kiln.Back.Value<T> value;
    private final ValueSet<T> values;

    /** Kiln's own: an option holding {@code value}. */
    public OptionInstance(T value) {
        this(null, value, v -> java.util.Optional.ofNullable(v));
    }

    /** Kiln's own: Horizon's option {@code key} (kiln.Back's keys), holding {@code value}, taking what {@code values} takes. */
    public OptionInstance(String key, T value, ValueSet<T> values) {
        this.value = new kiln.Back.Value<>(key, value);
        this.values = values;
    }

    /** The values the option takes, as the game's: a range of whole numbers for the field of view and the distances. */
    public ValueSet<T> values() {
        return values;
    }

    /** What an option takes: a value as it is, the nearest it takes, or none. */
    public interface ValueSet<T> {
        java.util.Optional<T> validateValue(T value);
    }

    /** Whole numbers from {@code minInclusive} to {@code maxInclusive}. */
    public record IntRange(int minInclusive, int maxInclusive) implements ValueSet<Integer> {
        @Override
        public java.util.Optional<Integer> validateValue(Integer value) {
            return value != null && value >= minInclusive && value <= maxInclusive ? java.util.Optional.of(value)
                    : java.util.Optional.empty();
        }
    }

    public T get() {
        return value.get();
    }

    public void set(T value) {
        this.value.set(value);
    }

    /** Kiln's own: the value the game said. */
    public void kiln$value(T value) {
        this.value.game(value);
    }

    /** Kiln's own: the option as Kiln sends it back to Horizon when a mod changed it. */
    public kiln.Back.Value<T> kiln$back() {
        return value;
    }
}
