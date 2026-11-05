package net.minecraft.nbt;

import kiln.NbtTags;

/** Kiln's stand-in for {@code ShortTag}: a short. */
public record ShortTag(short value) implements NumericTag {
    public static ShortTag valueOf(short value) {
        return new ShortTag(value);
    }

    @Override
    public byte getId() {
        return TAG_SHORT;
    }

    @Override
    public ShortTag copy() {
        return this;
    }

    @Override
    public Number box() {
        return value;
    }

    @Override
    public Object kiln$value() {
        return (Short) value;
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
