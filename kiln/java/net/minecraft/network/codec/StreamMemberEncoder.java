package net.minecraft.network.codec;

/** Kiln's stand-in for {@code StreamMemberEncoder}: a value that writes itself to a buffer. */
@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T value, O buffer);
}
