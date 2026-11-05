package net.minecraft.nbt;

import java.util.Arrays;
import kiln.NbtTags;

/** Kiln's stand-in for {@code IntArrayTag}: an array of ints. */
public final class IntArrayTag implements Tag {
    private int[] data;

    public IntArrayTag(int[] data) {
        this.data = data;
    }

    public int[] getAsIntArray() {
        return data;
    }

    public int size() {
        return data.length;
    }

    @Override
    public byte getId() {
        return TAG_INT_ARRAY;
    }

    @Override
    public IntArrayTag copy() {
        return new IntArrayTag(data.clone());
    }

    @Override
    public Object kiln$value() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IntArrayTag other && Arrays.equals(data, other.data);
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
