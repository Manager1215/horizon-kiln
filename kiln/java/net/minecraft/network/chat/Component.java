package net.minecraft.network.chat;

/** Kiln's stand-in for {@code Component}: chat text. Kiln keeps only the plain text. */
public interface Component {
    /** The text as the player reads it. */
    String getString();

    static MutableComponent literal(String text) {
        return new MutableComponent(text);
    }
}
