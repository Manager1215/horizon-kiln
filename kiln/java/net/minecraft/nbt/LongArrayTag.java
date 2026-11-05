package net.minecraft.nbt;

import java.util.Arrays;
import kiln.NbtTags;

/** Kiln's stand-in for {@code LongArrayTag}: an array of longs. */
public final class LongArrayTag implements Tag {
    private long[] data;

    public LongArrayTag(long[] data) {
        this.data = data;
    }

    public long[] getAsLongArray() {
        return data;
    }

    public int size() {
        return data.length;
    }

    @Override
    public byte getId() {
        return TAG_LONG_ARRAY;
    }

    @Override
    public LongArrayTag copy() {
        return new LongArrayTag(data.clone());
    }

    @Override
    public Object kiln$value() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LongArrayTag other && Arrays.equals(data, other.data);
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
