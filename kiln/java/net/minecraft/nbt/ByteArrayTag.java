package net.minecraft.nbt;

import java.util.Arrays;
import kiln.NbtTags;

/** Kiln's stand-in for {@code ByteArrayTag}: an array of bytes. */
public final class ByteArrayTag implements Tag {
    private byte[] data;

    public ByteArrayTag(byte[] data) {
        this.data = data;
    }

    public byte[] getAsByteArray() {
        return data;
    }

    public int size() {
        return data.length;
    }

    @Override
    public byte getId() {
        return TAG_BYTE_ARRAY;
    }

    @Override
    public ByteArrayTag copy() {
        return new ByteArrayTag(data.clone());
    }

    @Override
    public Object kiln$value() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteArrayTag other && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
