package net.minecraft.nbt;

import kiln.NbtTags;

/** Kiln's stand-in for {@code LongTag}: a long. */
public record LongTag(long value) implements NumericTag {
    public static LongTag valueOf(long value) {
        return new LongTag(value);
    }

    @Override
    public byte getId() {
        return TAG_LONG;
    }

    @Override
    public LongTag copy() {
        return this;
    }

    @Override
    public Number box() {
        return value;
    }

    @Override
    public Object kiln$value() {
        return (Long) value;
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
