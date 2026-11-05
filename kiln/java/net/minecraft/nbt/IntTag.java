package net.minecraft.nbt;

import kiln.NbtTags;

/** Kiln's stand-in for {@code IntTag}: a int. */
public record IntTag(int value) implements NumericTag {
    public static IntTag valueOf(int value) {
        return new IntTag(value);
    }

    @Override
    public byte getId() {
        return TAG_INT;
    }

    @Override
    public IntTag copy() {
        return this;
    }

    @Override
    public Number box() {
        return value;
    }

    @Override
    public Object kiln$value() {
        return (Integer) value;
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
