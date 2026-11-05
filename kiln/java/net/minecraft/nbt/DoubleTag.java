package net.minecraft.nbt;

import kiln.NbtTags;

/** Kiln's stand-in for {@code DoubleTag}: a double. */
public record DoubleTag(double value) implements NumericTag {
    public static DoubleTag valueOf(double value) {
        return new DoubleTag(value);
    }

    @Override
    public byte getId() {
        return TAG_DOUBLE;
    }

    @Override
    public DoubleTag copy() {
        return this;
    }

    @Override
    public Number box() {
        return value;
    }

    @Override
    public Object kiln$value() {
        return (Double) value;
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
