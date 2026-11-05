package net.minecraft.client;

/** Kiln's stand-in for {@code OptionInstance}: one of the game's options and its value, as the game said. */
public class OptionInstance<T> {
    private T value;

    /** Kiln's own: an option holding {@code value}. */
    public OptionInstance(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    /** What a mod sets stays in Java: the game's options are its own. */
    public void set(T value) {
        this.value = value;
    }

    /** Kiln's own: the value the game said. */
    public void kiln$value(T value) {
        this.value = value;
    }
}
