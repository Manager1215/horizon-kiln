package net.minecraft.nbt;

import kiln.NbtTags;

/** Kiln's stand-in for {@code FloatTag}: a float. */
public record FloatTag(float value) implements NumericTag {
    public static FloatTag valueOf(float value) {
        return new FloatTag(value);
    }

    @Override
    public byte getId() {
        return TAG_FLOAT;
    }

    @Override
    public FloatTag copy() {
        return this;
    }

    @Override
    public Number box() {
        return value;
    }

    @Override
    public Object kiln$value() {
        return (Float) value;
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
