package net.minecraft.network.codec;

/** Kiln's stand-in for {@code StreamEncoder}: writes a value to a buffer. */
@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O buffer, T value);
}
