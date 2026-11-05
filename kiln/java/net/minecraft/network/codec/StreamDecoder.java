package net.minecraft.network.codec;

/** Kiln's stand-in for {@code StreamDecoder}: reads a value from a buffer. */
@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I buffer);
}
