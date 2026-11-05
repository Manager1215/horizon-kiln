package net.minecraft.nbt;

import kiln.NbtTags;

/** Kiln's stand-in for {@code ByteTag}: a byte. */
public record ByteTag(byte value) implements NumericTag {
    public static final ByteTag ZERO = new ByteTag((byte) 0);
    public static final ByteTag ONE = new ByteTag((byte) 1);

    public static ByteTag valueOf(boolean value) {
        return value ? ONE : ZERO;
    }

    public static ByteTag valueOf(byte value) {
        return new ByteTag(value);
    }

    @Override
    public byte getId() {
        return TAG_BYTE;
    }

    @Override
    public ByteTag copy() {
        return this;
    }

    @Override
    public Number box() {
        return value;
    }

    @Override
    public Object kiln$value() {
        return (Byte) value;
    }

    @Override
    public String toString() {
        return NbtTags.text(this);
    }
}
